package slimeknights.tconstruct.library.recipe.tinkerstation.building;

import com.google.gson.JsonObject;
import net.minecraft.item.crafting.IRecipeSerializer;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.JSONUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistryEntry;
import slimeknights.mantle.recipe.RecipeHelper;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.tools.ToolCore;

import javax.annotation.Nullable;

public class ToolBuildingRecipeSerializer extends ForgeRegistryEntry<IRecipeSerializer<?>> implements IRecipeSerializer<ToolBuildingRecipe> {

  @Override
  public ToolBuildingRecipe read(ResourceLocation recipeId, JsonObject json) {
    String group = JSONUtils.getString(json, "group", "");

    // output fetch as a tinkerable item, its an error if it does not implement that interface
    ToolCore item = RecipeHelper.deserializeItem(JSONUtils.getString(json, "output"), "output", ToolCore.class);

    return new ToolBuildingRecipe(recipeId, group, item);
  }

  @Nullable
  @Override
  public ToolBuildingRecipe read(ResourceLocation recipeId, PacketBuffer buffer) {
    try {
      String group = buffer.readString(32767);
      ToolCore result = RecipeHelper.readItem(buffer, ToolCore.class);

      return new ToolBuildingRecipe(recipeId, group, result);
    }
    catch (Exception e) {
      TConstruct.log.error("Error reading tool building recipe from packet.", e);
      throw e;
    }
  }

  @Override
  public void write(PacketBuffer buffer, ToolBuildingRecipe recipe) {
    try {
      buffer.writeString(recipe.group);
      RecipeHelper.writeItem(buffer, recipe.output);
    }
    catch (Exception e) {
      TConstruct.log.error("Error writing tool building recipe to packet.", e);
      throw e;
    }
  }
}
