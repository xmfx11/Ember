package com.ember.player.factory

import com.ember.player.IPlayerCore
import com.ember.player.PlayerType
import com.ember.player.core.ExoPlayerCore

/**
 * 播放器内核工厂
 *
 * 使用工厂模式创建具体内核实例。
 * v0.01 默认仅 EXO 内核可用；MPV/VLC 为预留扩展点。
 */
object PlayerFactory {

    fun create(type: PlayerType): IPlayerCore = when (type) {
        PlayerType.EXO -> ExoPlayerCore()
        // MPV/VLC 需引入对应依赖后启用：
        // PlayerType.MPV -> MpvCore()
        // PlayerType.VLC -> VlcCore()
        PlayerType.MPV -> ExoPlayerCore()  // 暂时回退到 Exo
        PlayerType.VLC -> ExoPlayerCore()  // 暂时回退到 Exo
    }

    fun createDefault(): IPlayerCore = create(PlayerType.EXO)
}
