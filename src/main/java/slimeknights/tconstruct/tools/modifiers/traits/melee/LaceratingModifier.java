package slimeknights.tconstruct.tools.modifiers.traits.melee;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.hook.combat.MeleeHitModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.ranged.ProjectileHitModifierHook;
import slimeknights.tconstruct.library.module.ModuleHookMap.Builder;
import slimeknights.tconstruct.library.tools.context.ToolAttackContext;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ModifierNBT;
import slimeknights.tconstruct.library.tools.nbt.NamespacedNBT;
import slimeknights.tconstruct.tools.TinkerModifiers;

import javax.annotation.Nullable;

public class LaceratingModifier extends Modifier implements ProjectileHitModifierHook, MeleeHitModifierHook {
  /** Applies the effect to the target */
  private static void applyEffect(LivingEntity target, int level) {
    // potions are 0 indexed instead of 1 indexed
    // 81 ticks will do about 5 damage at level 1
    TinkerModifiers.bleeding.get().apply(target, 1 + 20 * (2 + (RANDOM.nextInt(level + 3))), level - 1, true);
  }

  @Override
  protected void registerHooks(Builder hookBuilder) {
    hookBuilder.addHook(this, ModifierHooks.PROJECTILE_HIT, ModifierHooks.MELEE_HIT);
  }

  @Override
  public void afterMeleeHit(IToolStackView tool, ModifierEntry modifier, ToolAttackContext context, float damageDealt) {
    // 50% chance of applying
    LivingEntity target = context.getLivingTarget();
    if (target != null && context.isFullyCharged() && target.isAlive() && RANDOM.nextFloat() < 0.50f) {
      // set entity so the potion is attributed as a player kill
      target.setLastHurtMob(context.getAttacker());
      applyEffect(target, modifier.getLevel());
    }
  }

  @Override
  public boolean onProjectileHitEntity(ModifierNBT modifiers, NamespacedNBT persistentData, ModifierEntry modifier, Projectile projectile, EntityHitResult hit, @Nullable LivingEntity attacker, @Nullable LivingEntity target) {
    if (target != null && (!(projectile instanceof AbstractArrow arrow) || arrow.isCritArrow()) && target.isAlive() && RANDOM.nextFloat() < 0.50f) {
      Entity owner = projectile.getOwner();
      if (owner != null) {
        target.setLastHurtMob(owner);
      }
      applyEffect(target, modifier.getLevel());
    }
    return false;
  }
}
