package slimeknights.tconstruct.library.modifiers.dynamic;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import io.netty.handler.codec.DecoderException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.util.GsonHelper;
import slimeknights.mantle.data.registry.GenericLoaderRegistry.IGenericLoader;
import slimeknights.mantle.util.JsonHelper;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHook;
import slimeknights.tconstruct.library.modifiers.TinkerHooks;
import slimeknights.tconstruct.library.modifiers.impl.BasicModifier;
import slimeknights.tconstruct.library.modifiers.modules.ModifierModule;
import slimeknights.tconstruct.library.modifiers.modules.ModifierModule.ModuleWithHooks;
import slimeknights.tconstruct.library.modifiers.util.ModifierLevelDisplay;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Modifier consisting of many composed hooks, used in datagen as a serialized modifier. */
public class ComposableModifier extends BasicModifier {
  private final List<ModuleWithHooks> modules;

  /**
   * Creates a new instance
   * @param levelDisplay     Level display
   * @param tooltipDisplay   Tooltip display
   * @param priority         If the value is {@link Integer#MIN_VALUE}, assumed unset for datagen
   * @param modules          Modules for this modifier
   */
  protected ComposableModifier(ModifierLevelDisplay levelDisplay, TooltipDisplay tooltipDisplay, int priority, List<ModuleWithHooks> modules) {
    super(ModifierModule.createMap(modules), levelDisplay, tooltipDisplay, priority);
    this.modules = modules;
  }

  /** Creates a builder instance for datagen */
  public static Builder builder() {
    return new Builder();
  }

  @Override
  public IGenericLoader<? extends Modifier> getLoader() {
    return LOADER;
  }

  @Override
  public Component getDisplayName(IToolStackView tool, ModifierEntry entry) {
    return getHook(TinkerHooks.DISPLAY_NAME).getDisplayName(tool, entry, entry.getDisplayName());
  }

  /** Determines when this modifier shows in tooltips */
  public enum TooltipDisplay { ALWAYS, TINKER_STATION, NEVER }

  /** Computes the recommended priority for a set of modifier modules */
  private static int computePriority(List<ModuleWithHooks> modules) {
    // poll all modules to find who has a priority preference
    List<ModifierModule> priorityModules = new ArrayList<>();
    for (ModuleWithHooks module : modules) {
      if (module.module().getPriority() != null) {
        priorityModules.add(module.module());
      }
    }
    if (!priorityModules.isEmpty()) {
      //noinspection ConstantConditions  validated nonnull above
      int firstPriority = priorityModules.get(0).getPriority();

      // check if any module disagrees with the first priority, if so we need a warning (but not more than one warning)
      for (int i = 1; i < priorityModules.size(); i++) {
        //noinspection ConstantConditions  validated nonnull above
        if (priorityModules.get(i).getPriority() != firstPriority) {
          TConstruct.LOG.warn("Multiple modules disagree on the preferred priority for composable modifier, choosing priority {}. Set the priority manually to silence this warning. All opinions: \n{}", firstPriority,
                              priorityModules.stream()
                                             .map(module -> "* " + module + ": " + module.getPriority())
                                             .collect(Collectors.joining("\n")));
          break;
        }
      }
      return firstPriority;
    }
    return Modifier.DEFAULT_PRIORITY;
  }

  public static IGenericLoader<ComposableModifier> LOADER = new IGenericLoader<>() {
    @Override
    public ComposableModifier deserialize(JsonObject json) {
      ModifierLevelDisplay level_display = ModifierLevelDisplay.LOADER.getIfPresent(json, "level_display");
      TooltipDisplay tooltipDisplay = TooltipDisplay.ALWAYS;
      if (json.has("tooltip_display")) {
        tooltipDisplay = JsonHelper.getAsEnum(json, "tooltip_display", TooltipDisplay.class);
      }
      List<ModuleWithHooks> modules = JsonHelper.parseList(json, "modules", ModuleWithHooks::deserialize);
      int priority;
      if (json.has("priority")) {
        priority = GsonHelper.getAsInt(json, "priority");
      } else {
        priority = computePriority(modules);
      }

      // convert illegal argument to json syntax, bit more expected in this context
      // TODO: figure out the best way to do this in loadables
      try {
        return new ComposableModifier(level_display, tooltipDisplay, priority, modules);
      } catch (IllegalArgumentException e) {
        throw new JsonSyntaxException(e.getMessage(), e);
      }
    }

    @Override
    public void serialize(ComposableModifier object, JsonObject json) {
      json.add("level_display", ModifierLevelDisplay.LOADER.serialize(object.levelDisplay));
      json.addProperty("tooltip_display", object.tooltipDisplay.name().toLowerCase(Locale.ROOT));
      if (object.priority != Integer.MIN_VALUE) {
        json.addProperty("priority", object.priority);
      }
      JsonArray modules = new JsonArray();
      for (ModuleWithHooks module : object.modules) {
        modules.add(module.serialize());
      }
      json.add("modules", modules);
    }

    @Override
    public ComposableModifier fromNetwork(FriendlyByteBuf buffer) {
      ModifierLevelDisplay levelDisplay = ModifierLevelDisplay.LOADER.decode(buffer);
      TooltipDisplay tooltipDisplay = buffer.readEnum(TooltipDisplay.class);
      int priority = buffer.readInt();
      int moduleCount = buffer.readVarInt();
      ImmutableList.Builder<ModuleWithHooks> builder = ImmutableList.builder();
      for (int i = 0; i < moduleCount; i++) {
        builder.add(ModuleWithHooks.fromNetwork(buffer));
      }
      try {
        return new ComposableModifier(levelDisplay, tooltipDisplay, priority, builder.build());
      } catch (IllegalArgumentException e) {
        throw new DecoderException(e.getMessage(), e);
      }
    }

    @Override
    public void toNetwork(ComposableModifier object, FriendlyByteBuf buffer) {
      ModifierLevelDisplay.LOADER.encode(buffer, object.levelDisplay);
      buffer.writeEnum(object.tooltipDisplay);
      buffer.writeInt(object.priority);
      buffer.writeVarInt(object.modules.size());
      for (ModuleWithHooks module : object.modules) {
        module.toNetwork(buffer);
      }
    }
  };

  /** Builder for a composable modifier instance */
  @SuppressWarnings("UnusedReturnValue")  // it's a builder
  @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
  @Accessors(fluent = true)
  public static class Builder {
    @Setter
    private ModifierLevelDisplay levelDisplay = ModifierLevelDisplay.DEFAULT;
    @Setter
    private TooltipDisplay tooltipDisplay = TooltipDisplay.ALWAYS;
    /** {@link Integer#MIN_VALUE} is an internal value used to represent unset for datagen, to distinguish unset from {@link Modifier#DEFAULT_PRIORITY} */
    @Setter
    private int priority = Integer.MIN_VALUE;
    private final ImmutableList.Builder<ModuleWithHooks> modules = ImmutableList.builder();

    /** Adds a module to the builder */
    public final Builder addModule(ModifierModule module) {
      modules.add(new ModuleWithHooks(module, Collections.emptyList()));
      return this;
    }

    /** Adds a module to the builder */
    public final Builder addModules(ModifierModule... modules) {
      for (ModifierModule module : modules) {
        addModule(module);
      }
      return this;
    }

    /** Adds a module to the builder */
    @SuppressWarnings("UnusedReturnValue")
    @SafeVarargs
    public final <T extends ModifierModule> Builder addModule(T object, ModifierHook<? super T>... hooks) {
      modules.add(new ModuleWithHooks(object, List.of(hooks)));
      return this;
    }

    /** Builds the final instance */
    public ComposableModifier build() {
      List<ModuleWithHooks> modules = this.modules.build();
      if (priority == Integer.MIN_VALUE) {
        // call computePriority if we did not set one so we get the warning if multiple modules wish to set the priority
        computePriority(modules);
      }
      return new ComposableModifier(levelDisplay, tooltipDisplay, priority, modules);
    }
  }
}
