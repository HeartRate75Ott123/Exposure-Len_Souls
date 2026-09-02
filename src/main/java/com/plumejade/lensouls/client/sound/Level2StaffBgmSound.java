package com.plumejade.lensouls.client.sound;

import com.plumejade.lensouls.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

/**
 * N公司2级员工 BGM 实例（客户端）—— 仿 Legendary-Monsters {@code SoundBossMusic}。
 * <p>
 * <ul>
 *   <li>{@link SoundSource#MUSIC}：音量受游戏「音乐」滑块控制（原生音乐选项卡）；</li>
 *   <li>静态缓冲（sounds.json 非 stream）+ {@code looping=true}：SoundEngine 原生循环；</li>
 *   <li>音量由 {@link Level2StaffBossBgmHandler} 按玩家距离写 {@code targetVolume}，
 *       本实例 tick 内平滑逼近 → 进出 32 格范围淡入淡出；</li>
 *   <li>音量归零 / Boss 消失后自动 stop 并清除单例。</li>
 * </ul>
 */
public class Level2StaffBgmSound extends AbstractTickableSoundInstance {

    /** 音量逼近系数（每 tick 朝目标靠近比例） */
    private static final float SMOOTH = 0.06f;
    /** 淡出完成阈值 */
    private static final float STOP_THRESHOLD = 0.004f;

    private Entity boss;
    private final float baseVolume;
    private float targetVolume = 0f;
    private int tickCount = 0;

    public Level2StaffBgmSound(Entity boss) {
        super(ModSounds.LEVEL2_STAFF_BGM.get(), SoundSource.MUSIC, RandomSource.create());
        this.boss = boss;
        this.looping = true;
        this.delay = 0;
        this.attenuation = net.minecraft.client.resources.sounds.SoundInstance.Attenuation.NONE;
        this.relative = false;
        this.baseVolume = 1.0f;
        this.volume = 0.001f; // 非零启动（SoundEngine 跳过 volume==0），由 tick 淡入
        this.pitch = 1.0f;
        syncPosition();
    }

    /** 静音也可启动（依赖音量淡入） */
    @Override
    public boolean canStartSilent() {
        return true;
    }

    /** 单例互斥：仅当本实例仍是被播放的那个 */
    @Override
    public boolean canPlaySound() {
        return Level2StaffBossBgmHandler.isCurrent(this);
    }

    /** 外部设置目标音量（0~1） */
    public void setTargetVolume(float target) {
        this.targetVolume = Mth.clamp(target, 0f, 1f);
    }

    /** 重新绑定 Boss（同 BGM 不同目标时复用实例） */
    public void setBoss(Entity newBoss) {
        this.boss = newBoss;
    }

    public Entity getBoss() {
        return boss;
    }

    public boolean isBossValid() {
        return boss != null && boss.isAlive() && !boss.isSilent();
    }

    private void syncPosition() {
        if (boss == null) return;
        this.x = boss.getX();
        this.y = boss.getY();
        this.z = boss.getZ();
    }

    @Override
    public void tick() {
        tickCount++;
        syncPosition();

        if (!isBossValid()) {
            // Boss 死亡/静默 → 目标音量归零淡出
            this.targetVolume = 0f;
        }

        // 音量平滑逼近目标
        this.volume += (this.targetVolume - this.volume) * SMOOTH;
        if (this.volume > baseVolume) this.volume = baseVolume;

        // 每 100 tick 停一次原版音乐，避免 BGM 与原版音乐叠放
        if (tickCount % 100 == 0) {
            Minecraft.getInstance().getMusicManager().stopPlaying();
        }

        // 淡出完成且无目标 → 停止并清单例
        if (!isBossValid() && this.targetVolume <= 0f && this.volume <= STOP_THRESHOLD) {
            this.stop();
            Level2StaffBossBgmHandler.clearInstance();
        }
    }
}
