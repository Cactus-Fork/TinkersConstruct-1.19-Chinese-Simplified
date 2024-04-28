package slimeknights.tconstruct.tools.recipe;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.ItemHandlerHelper;
import slimeknights.mantle.data.loadable.common.ItemStackLoadable;
import slimeknights.mantle.data.loadable.field.ContextKey;
import slimeknights.mantle.data.loadable.field.LoadableField;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.data.predicate.IJsonPredicate;
import slimeknights.mantle.recipe.ingredient.SizedIngredient;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.json.predicate.modifier.ModifierPredicate;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.recipe.ITinkerableContainer;
import slimeknights.tconstruct.library.recipe.RecipeResult;
import slimeknights.tconstruct.library.recipe.modifiers.ModifierRecipeLookup;
import slimeknights.tconstruct.library.recipe.modifiers.ModifierSalvage;
import slimeknights.tconstruct.library.recipe.modifiers.adding.ModifierRecipe;
import slimeknights.tconstruct.library.recipe.worktable.AbstractWorktableRecipe;
import slimeknights.tconstruct.library.tools.item.IModifiableDisplay;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.tools.TinkerModifiers;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Predicate;

public class ModifierRemovalRecipe extends AbstractWorktableRecipe {
  private static final Component TITLE = TConstruct.makeTranslation("recipe", "remove_modifier.title");
  private static final Component DESCRIPTION = TConstruct.makeTranslation("recipe", "remove_modifier.description");
  private static final Component NO_MODIFIERS = TConstruct.makeTranslation("recipe", "remove_modifier.no_modifiers");
  public static final SizedIngredient DEFAULT_TOOLS = SizedIngredient.of(AbstractWorktableRecipe.DEFAULT_TOOLS);

  protected static final LoadableField<SizedIngredient,ModifierRemovalRecipe> TOOLS_FIELD = SizedIngredient.LOADABLE.defaultField("tools", DEFAULT_TOOLS, true, r -> r.sizedTool);
  protected static final LoadableField<List<ItemStack>,ModifierRemovalRecipe> LEFTOVERS_FIELD = ItemStackLoadable.REQUIRED_STACK_NBT.list(0).defaultField("leftovers", List.of(), r -> r.leftovers);
  protected static final LoadableField<IJsonPredicate<ModifierId>,ModifierRemovalRecipe> MODIFIER_PREDICATE_FIELD = ModifierPredicate.LOADER.defaultField("modifier_predicate", false, r -> r.modifierPredicate);

  /** Recipe loadable */
  public static final RecordLoadable<ModifierRemovalRecipe> LOADER = RecordLoadable.create(ContextKey.ID.requiredField(), TOOLS_FIELD, INPUTS_FIELD, LEFTOVERS_FIELD, MODIFIER_PREDICATE_FIELD, ModifierRemovalRecipe::new);

  private final SizedIngredient sizedTool;
  private final List<ItemStack> leftovers;
  private final IJsonPredicate<ModifierId> modifierPredicate;

  protected final Predicate<ModifierEntry> entryPredicate;
  private List<ModifierEntry> displayModifiers;

  public ModifierRemovalRecipe(ResourceLocation id, SizedIngredient toolRequirement, List<SizedIngredient> inputs, List<ItemStack> leftovers, IJsonPredicate<ModifierId> modifierPredicate) {
    super(id, toolRequirement.getIngredient(), inputs);
    this.sizedTool = toolRequirement;
    this.leftovers = leftovers;
    this.modifierPredicate = modifierPredicate;
    this.entryPredicate = mod -> modifierPredicate.matches(mod.getId());
  }

  @Override
  public Component getTitle() {
    return TITLE;
  }

  @Override
  public boolean matches(ITinkerableContainer inv, Level world) {
    if (!sizedTool.test(inv.getTinkerableStack())) {
      return false;
    }
    return ModifierRecipe.checkMatch(inv, inputs);
  }

  /** Filters the given modifier list */
  protected List<ModifierEntry> filter(@Nullable IToolStackView tool, List<ModifierEntry> modifiers) {
    if (modifierPredicate != ModifierPredicate.ANY) {
      return modifiers.stream().filter(entryPredicate).toList();
    }
    return modifiers;
  }

  @Override
  public List<ModifierEntry> getModifierOptions(@Nullable ITinkerableContainer inv) {
    if (inv == null) {
      if (displayModifiers == null) {
        displayModifiers = filter(null, ModifierRecipeLookup.getRecipeModifierList());
      }
      return displayModifiers;
    }
    return filter(inv.getTinkerable(), inv.getTinkerable().getUpgrades().getModifiers());
  }

  @Override
  public Component getDescription(@Nullable ITinkerableContainer inv) {
    if (inv != null && inv.getTinkerable().getUpgrades().getModifiers().stream().noneMatch(entryPredicate)) {
      return NO_MODIFIERS;
    }
    return DESCRIPTION;
  }

  @Override
  public RecipeResult<ToolStack> getResult(ITinkerableContainer inv, ModifierEntry entry) {
    ToolStack tool = inv.getTinkerable();

    // salvage
    tool = tool.copy();
    ModifierId modifierId = entry.getId();
    ModifierSalvage salvage = ModifierRecipeLookup.getSalvage(inv.getTinkerableStack(), tool, modifierId, entry.getLevel());

    // restore the slots
    if (salvage != null) {
      salvage.updateTool(tool);
    }

    // first remove hook, primarily for removing raw NBT which is highly discouraged using
    int newLevel = tool.getModifierLevel(modifierId) - 1;
    Modifier modifier = entry.getModifier();
    if (newLevel <= 0) {
      modifier.getHook(ModifierHooks.RAW_DATA).removeRawData(tool, modifier, tool.getRestrictedNBT());
    }

    // remove the actual modifier
    tool.removeModifier(modifierId, 1);

    // ensure the tool is still valid
    Component error = tool.tryValidate();
    if (error != null) {
      return RecipeResult.failure(error);
    }
    // if this was the last level, validate the tool is still valid without it
    if (newLevel <= 0) {
      error = modifier.getHook(ModifierHooks.REMOVE).onRemoved(tool, modifier);
      if (error != null) {
        return RecipeResult.failure(error);
      }
    }
    // successfully removed
    return RecipeResult.success(tool);
  }

  @Override
  public int toolResultSize() {
    return 64;
  }

  @Override
  public void updateInputs(IToolStackView result, ITinkerableContainer.Mutable inv, ModifierEntry selected, boolean isServer) {
    super.updateInputs(result, inv, selected, isServer);
    if (isServer) {
      for (ItemStack stack : leftovers) {
        inv.giveItem(stack.copy());
      }
    }
  }

  @Override
  public RecipeSerializer<?> getSerializer() {
    return TinkerModifiers.removeModifierSerializer.get();
  }



  /* JEI */

  /** Gets a list of tools to display */
  @Override
  public List<ItemStack> getInputTools() {
    if (tools == null) {
      tools = sizedTool.getMatchingStacks().stream().map(stack -> {
        ItemStack tool = IModifiableDisplay.getDisplayStack(stack.getItem());
        if (stack.getCount() > 1) {
          tool = ItemHandlerHelper.copyStackWithSize(tool, stack.getCount());
        }
        return tool;
      }).toList();
    }
    return tools;
  }
}
