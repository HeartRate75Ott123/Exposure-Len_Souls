package com.plumejade.lensouls.mixin.compat;

import com.plumejade.lensouls.boss.ProjectileImmunityHelper;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 传奇怪物 / 灾变：<b>弹射物免疫全局解除</b>（需求第 9 项，原为「定身去被动」的一部分，现下放到全局）。
 * <p>
 * 这两个模组的 boss 有两套写法，本文件各有一组：
 * <ol>
 *   <li><b>标签判定</b>（{@code source.is(DamageTypeTags.IS_PROJECTILE)}）→ {@link ProjectileImmunityHelper#allowProjectile}
 *       <ul>
 *         <li>传奇怪物：Shulker_Mimic（完全免疫）、FlamebornGuard / FlamebornWarrior / AnnihilationPursuer（瞬移闪避）</li>
 *         <li>灾变：Kobolediator / Wadjet（{@code canBlockDamageSource} 格挡）、Scylla（投射物招架值 +2）</li>
 *       </ul>
 *   </li>
 *   <li><b>类型判定</b>（{@code source.getDirectEntity() instanceof AbstractArrow / ThrownPotion}）→
 *       {@link ProjectileImmunityHelper#hideProjectileDirectEntity}：把外来投射物的直接实体置空，
 *       使 {@code instanceof} 免伤分支失效。传奇怪物 19 个类（判定还被其
 *       {@code ModConfig.MobConfig.*projectile} 配置项包着，置空后配置值不再影响结果）、
 *       灾变 5 个类。</li>
 * </ol>
 * <b>刻意未包含</b>：
 * <ul>
 *   <li>{@code Ignis_Entity#hurt} —— 其 {@code IS_PROJECTILE} / 直接实体判定用于「吸收自身深渊火球回盾」
 *       与盾反前置，改掉会让投射物反而触发盾反；</li>
 *   <li>{@code Ignited_Revenant_Entity#hurt} / {@code Royal_Draugr_Entity#hurt} —— 同上，盾反前置；</li>
 *   <li>各 boss 对 <b>自己发射的弹射物</b> 的免疫（{@code getOwner() == self} 时原样放行）——
 *       否则灾变末影守卫会被自己的子弹打伤、Kobolediator 会被自己的毒镖打伤；</li>
 *   <li>{@code AreaEffectCloud}（滞留药水云）免疫 —— 它不属于「弹射物」范畴。</li>
 * </ul>
 * 所有判定点均由 {@code javap -c} 反编译<b>实际安装的 mod jar</b>核实（非参考源码版本）。
 */
public final class ProjectileImmunityMixins {

    private ProjectileImmunityMixins() {}

    /** {@code DamageSource#is(TagKey)} 描述符（本 mixin 全部 remap = false，用官方名） */
    private static final String IS_TAG =
            "Lnet/minecraft/world/damagesource/DamageSource;is(Lnet/minecraft/tags/TagKey;)Z";

    /** {@code DamageSource#getDirectEntity()} 描述符 */
    private static final String GET_DIRECT =
            "Lnet/minecraft/world/damagesource/DamageSource;getDirectEntity()Lnet/minecraft/world/entity/Entity;";


    // ==================== 一、标签判定：DamageSource#is(IS_PROJECTILE) ====================

    @Mixin(targets = "net.miauczel.legendary_monsters.entity.AnimatedMonster.Mobs.ShulkerTower.Shulker_MimicEntity", remap = false)
    public abstract static class ShulkerMimic {
        @Redirect(method = "hurt", at = @At(value = "INVOKE", target = IS_TAG), remap = false)
        private boolean lensouls$noProjectileImmunity(DamageSource source, TagKey<DamageType> tag) {
            return ProjectileImmunityHelper.allowProjectile(source, tag);
        }
    }

    @Mixin(targets = "net.miauczel.legendary_monsters.entity.AnimatedMonster.Mobs.SpaceStation.Flameborn.FlamebornGuardEntity", remap = false)
    public abstract static class FlamebornGuard {
        @Redirect(method = "hurt", at = @At(value = "INVOKE", target = IS_TAG), remap = false)
        private boolean lensouls$noProjectileImmunity(DamageSource source, TagKey<DamageType> tag) {
            return ProjectileImmunityHelper.allowProjectile(source, tag);
        }
    }

    @Mixin(targets = "net.miauczel.legendary_monsters.entity.AnimatedMonster.Mobs.SpaceStation.Flameborn.FlamebornWarriorEntity", remap = false)
    public abstract static class FlamebornWarrior {
        @Redirect(method = "hurt", at = @At(value = "INVOKE", target = IS_TAG), remap = false)
        private boolean lensouls$noProjectileImmunity(DamageSource source, TagKey<DamageType> tag) {
            return ProjectileImmunityHelper.allowProjectile(source, tag);
        }
    }

    @Mixin(targets = "net.miauczel.legendary_monsters.entity.AnimatedMonster.Mobs.SpaceStation.Flameborn.AnnihilationPursuer.AnnihilationPursuerEntity", remap = false)
    public abstract static class AnnihilationPursuer {
        @Redirect(method = "hurt", at = @At(value = "INVOKE", target = IS_TAG), remap = false)
        private boolean lensouls$noProjectileImmunity(DamageSource source, TagKey<DamageType> tag) {
            return ProjectileImmunityHelper.allowProjectile(source, tag);
        }
    }

    @Mixin(targets = "com.github.L_Ender.cataclysm.entity.InternalAnimationMonster.Kobolediator_Entity", remap = false)
    public abstract static class Kobolediator {
        @Redirect(method = "canBlockDamageSource", at = @At(value = "INVOKE", target = IS_TAG), remap = false)
        private boolean lensouls$noProjectileImmunity(DamageSource source, TagKey<DamageType> tag) {
            return ProjectileImmunityHelper.allowProjectile(source, tag);
        }
    }

    @Mixin(targets = "com.github.L_Ender.cataclysm.entity.InternalAnimationMonster.Wadjet_Entity", remap = false)
    public abstract static class Wadjet {
        @Redirect(method = "canBlockDamageSource", at = @At(value = "INVOKE", target = IS_TAG), remap = false)
        private boolean lensouls$noProjectileImmunity(DamageSource source, TagKey<DamageType> tag) {
            return ProjectileImmunityHelper.allowProjectile(source, tag);
        }
    }

    @Mixin(targets = "com.github.L_Ender.cataclysm.entity.InternalAnimationMonster.IABossMonsters.Scylla.Scylla_Entity", remap = false)
    public abstract static class Scylla {
        @Redirect(method = "hurt", at = @At(value = "INVOKE", target = IS_TAG), remap = false)
        private boolean lensouls$noProjectileImmunity(DamageSource source, TagKey<DamageType> tag) {
            return ProjectileImmunityHelper.allowProjectile(source, tag);
        }
    }

    // ==================== 二、类型判定：getDirectEntity() instanceof 投射物 ====================

    @Mixin(targets = "net.miauczel.legendary_monsters.entity.AnimatedMonster.IAnimatedBoss.CloudGolem.Cloud_GolemEntity", remap = false)
    public abstract static class CloudGolem {
        @Redirect(method = "hurt", at = @At(value = "INVOKE", target = GET_DIRECT), remap = false)
        private Entity lensouls$noArrowImmunity(DamageSource source) {
            return ProjectileImmunityHelper.hideProjectileDirectEntity(source, (Entity) (Object) this);
        }
    }

    @Mixin(targets = "net.miauczel.legendary_monsters.entity.AnimatedMonster.Mobs.Frostbitten_GolemEntity", remap = false)
    public abstract static class FrostbittenGolem {
        @Redirect(method = "hurt", at = @At(value = "INVOKE", target = GET_DIRECT), remap = false)
        private Entity lensouls$noArrowImmunity(DamageSource source) {
            return ProjectileImmunityHelper.hideProjectileDirectEntity(source, (Entity) (Object) this);
        }
    }

    @Mixin(targets = "net.miauczel.legendary_monsters.entity.AnimatedMonster.Mobs.Lava_eaterEntity", remap = false)
    public abstract static class LavaEater {
        @Redirect(method = "hurt", at = @At(value = "INVOKE", target = GET_DIRECT), remap = false)
        private Entity lensouls$noArrowImmunity(DamageSource source) {
            return ProjectileImmunityHelper.hideProjectileDirectEntity(source, (Entity) (Object) this);
        }
    }

    @Mixin(targets = "net.miauczel.legendary_monsters.entity.AnimatedMonster.Mobs.Overgrown_colossusEntity", remap = false)
    public abstract static class OvergrownColossus {
        @Redirect(method = "hurt", at = @At(value = "INVOKE", target = GET_DIRECT), remap = false)
        private Entity lensouls$noArrowImmunity(DamageSource source) {
            return ProjectileImmunityHelper.hideProjectileDirectEntity(source, (Entity) (Object) this);
        }
    }

    @Mixin(targets = "net.miauczel.legendary_monsters.entity.AnimatedMonster.Mobs.SkeletosaurusEntity", remap = false)
    public abstract static class Skeletosaurus {
        @Redirect(method = "hurt", at = @At(value = "INVOKE", target = GET_DIRECT), remap = false)
        private Entity lensouls$noArrowImmunity(DamageSource source) {
            return ProjectileImmunityHelper.hideProjectileDirectEntity(source, (Entity) (Object) this);
        }
    }

    @Mixin(targets = "net.miauczel.legendary_monsters.entity.AnimatedMonster.Mobs.Warped_FungussusEntity", remap = false)
    public abstract static class WarpedFungussus {
        @Redirect(method = "hurt", at = @At(value = "INVOKE", target = GET_DIRECT), remap = false)
        private Entity lensouls$noArrowImmunity(DamageSource source) {
            return ProjectileImmunityHelper.hideProjectileDirectEntity(source, (Entity) (Object) this);
        }
    }

    @Mixin(targets = "net.miauczel.legendary_monsters.entity.AnimatedMonster.Mobs.Withered_AbominationEntity", remap = false)
    public abstract static class WitheredAbomination {
        @Redirect(method = "hurt", at = @At(value = "INVOKE", target = GET_DIRECT), remap = false)
        private Entity lensouls$noArrowImmunity(DamageSource source) {
            return ProjectileImmunityHelper.hideProjectileDirectEntity(source, (Entity) (Object) this);
        }
    }

    @Mixin(targets = "net.miauczel.legendary_monsters.entity.AnimatedMonster.Mobs.AncientStronghold.AmbusherEntity", remap = false)
    public abstract static class Ambusher {
        @Redirect(method = "hurt", at = @At(value = "INVOKE", target = GET_DIRECT), remap = false)
        private Entity lensouls$noArrowImmunity(DamageSource source) {
            return ProjectileImmunityHelper.hideProjectileDirectEntity(source, (Entity) (Object) this);
        }
    }

    @Mixin(targets = "net.miauczel.legendary_monsters.entity.AnimatedMonster.Mobs.AncientStronghold.Ancient_GuardianEntity", remap = false)
    public abstract static class AncientGuardian {
        @Redirect(method = "hurt", at = @At(value = "INVOKE", target = GET_DIRECT), remap = false)
        private Entity lensouls$noArrowImmunity(DamageSource source) {
            return ProjectileImmunityHelper.hideProjectileDirectEntity(source, (Entity) (Object) this);
        }
    }

    @Mixin(targets = "net.miauczel.legendary_monsters.entity.AnimatedMonster.Mobs.Chorusling.ChoruslingEntity", remap = false)
    public abstract static class Chorusling {
        @Redirect(method = "hurt", at = @At(value = "INVOKE", target = GET_DIRECT), remap = false)
        private Entity lensouls$noArrowImmunity(DamageSource source) {
            return ProjectileImmunityHelper.hideProjectileDirectEntity(source, (Entity) (Object) this);
        }
    }

    @Mixin(targets = "net.miauczel.legendary_monsters.entity.AnimatedMonster.Mobs.Chorusling.EndersentEntity", remap = false)
    public abstract static class Endersent {
        @Redirect(method = "hurt", at = @At(value = "INVOKE", target = GET_DIRECT), remap = false)
        private Entity lensouls$noArrowImmunity(DamageSource source) {
            return ProjectileImmunityHelper.hideProjectileDirectEntity(source, (Entity) (Object) this);
        }
    }

    @Mixin(targets = "net.miauczel.legendary_monsters.entity.AnimatedMonster.Mobs.CollapsedKingdom.HauntedGuardEntity", remap = false)
    public abstract static class HauntedGuard {
        @Redirect(method = "hurt", at = @At(value = "INVOKE", target = GET_DIRECT), remap = false)
        private Entity lensouls$noArrowImmunity(DamageSource source) {
            return ProjectileImmunityHelper.hideProjectileDirectEntity(source, (Entity) (Object) this);
        }
    }

    @Mixin(targets = "net.miauczel.legendary_monsters.entity.AnimatedMonster.Mobs.CollapsedKingdom.PosessedPaladinEntity", remap = false)
    public abstract static class PosessedPaladin {
        @Redirect(method = "hurt", at = @At(value = "INVOKE", target = GET_DIRECT), remap = false)
        private Entity lensouls$noArrowImmunity(DamageSource source) {
            return ProjectileImmunityHelper.hideProjectileDirectEntity(source, (Entity) (Object) this);
        }
    }

    @Mixin(targets = "net.miauczel.legendary_monsters.entity.AnimatedMonster.Mobs.CollapsedKingdom.OldKnights.HauntedKnightEntityOld", remap = false)
    public abstract static class HauntedKnightOld {
        @Redirect(method = "hurt", at = @At(value = "INVOKE", target = GET_DIRECT), remap = false)
        private Entity lensouls$noArrowImmunity(DamageSource source) {
            return ProjectileImmunityHelper.hideProjectileDirectEntity(source, (Entity) (Object) this);
        }
    }

    @Mixin(targets = "net.miauczel.legendary_monsters.entity.AnimatedMonster.Mobs.CollapsedKingdom.OldKnights.OldHauntedGuard", remap = false)
    public abstract static class OldHauntedGuard {
        @Redirect(method = "hurt", at = @At(value = "INVOKE", target = GET_DIRECT), remap = false)
        private Entity lensouls$noArrowImmunity(DamageSource source) {
            return ProjectileImmunityHelper.hideProjectileDirectEntity(source, (Entity) (Object) this);
        }
    }

    @Mixin(targets = "net.miauczel.legendary_monsters.entity.AnimatedMonster.Mobs.Pets.MossyGolemEntity", remap = false)
    public abstract static class MossyGolem {
        @Redirect(method = "hurt", at = @At(value = "INVOKE", target = GET_DIRECT), remap = false)
        private Entity lensouls$noArrowImmunity(DamageSource source) {
            return ProjectileImmunityHelper.hideProjectileDirectEntity(source, (Entity) (Object) this);
        }
    }

    @Mixin(targets = "net.miauczel.legendary_monsters.entity.AnimatedMonster.Mobs.Pets.SkeloraptorEntity", remap = false)
    public abstract static class Skeloraptor {
        @Redirect(method = "hurt", at = @At(value = "INVOKE", target = GET_DIRECT), remap = false)
        private Entity lensouls$noArrowImmunity(DamageSource source) {
            return ProjectileImmunityHelper.hideProjectileDirectEntity(source, (Entity) (Object) this);
        }
    }

    @Mixin(targets = "net.miauczel.legendary_monsters.entity.AnimatedMonster.Mobs.Pets.knights.FHauntedGuardEntity", remap = false)
    public abstract static class FHauntedGuard {
        @Redirect(method = "hurt", at = @At(value = "INVOKE", target = GET_DIRECT), remap = false)
        private Entity lensouls$noArrowImmunity(DamageSource source) {
            return ProjectileImmunityHelper.hideProjectileDirectEntity(source, (Entity) (Object) this);
        }
    }

    @Mixin(targets = "net.miauczel.legendary_monsters.entity.AnimatedMonster.Mobs.RuinedPyramid.DuneSentinelEntity", remap = false)
    public abstract static class DuneSentinel {
        @Redirect(method = "hurt", at = @At(value = "INVOKE", target = GET_DIRECT), remap = false)
        private Entity lensouls$noArrowImmunity(DamageSource source) {
            return ProjectileImmunityHelper.hideProjectileDirectEntity(source, (Entity) (Object) this);
        }
    }

    @Mixin(targets = "com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.Ender_Guardian_Entity", remap = false)
    public abstract static class CataEnderGuardian {
        @Redirect(method = "hurt", at = @At(value = "INVOKE", target = GET_DIRECT), remap = false)
        private Entity lensouls$noArrowImmunity(DamageSource source) {
            return ProjectileImmunityHelper.hideProjectileDirectEntity(source, (Entity) (Object) this);
        }
    }

    @Mixin(targets = "com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.The_Harbinger_Entity", remap = false)
    public abstract static class TheHarbinger {
        @Redirect(method = "hurt", at = @At(value = "INVOKE", target = GET_DIRECT), remap = false)
        private Entity lensouls$noArrowImmunity(DamageSource source) {
            return ProjectileImmunityHelper.hideProjectileDirectEntity(source, (Entity) (Object) this);
        }
    }

    @Mixin(targets = "com.github.L_Ender.cataclysm.entity.InternalAnimationMonster.IABossMonsters.Ancient_Remnant.Ancient_Remnant_Entity", remap = false)
    public abstract static class AncientRemnant {
        @Redirect(method = "hurt", at = @At(value = "INVOKE", target = GET_DIRECT), remap = false)
        private Entity lensouls$noArrowImmunity(DamageSource source) {
            return ProjectileImmunityHelper.hideProjectileDirectEntity(source, (Entity) (Object) this);
        }
    }

    @Mixin(targets = "com.github.L_Ender.cataclysm.entity.InternalAnimationMonster.Kobolediator_Entity", remap = false)
    public abstract static class KobolediatorHurt {
        @Redirect(method = "hurt", at = @At(value = "INVOKE", target = GET_DIRECT), remap = false)
        private Entity lensouls$noArrowImmunity(DamageSource source) {
            return ProjectileImmunityHelper.hideProjectileDirectEntity(source, (Entity) (Object) this);
        }
    }

    @Mixin(targets = "com.github.L_Ender.cataclysm.entity.InternalAnimationMonster.Wadjet_Entity", remap = false)
    public abstract static class WadjetHurt {
        @Redirect(method = "hurt", at = @At(value = "INVOKE", target = GET_DIRECT), remap = false)
        private Entity lensouls$noArrowImmunity(DamageSource source) {
            return ProjectileImmunityHelper.hideProjectileDirectEntity(source, (Entity) (Object) this);
        }
    }

}
