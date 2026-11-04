package xyz.hegke.client.mixin;

import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.hegke.client.script.action.ScriptKeys;

/**
 * 让脚本注入的按键状态优先于原版按键轮询。
 *
 * <p>原版每 tick 会用物理按键状态刷新 KeyBinding，单纯调用 setPressed 会被覆盖，
 * 表现为角色移动时断时续。此处从读取端拦截，避免与刷新逻辑竞争。</p>
 */
@Mixin(KeyBinding.class)
public abstract class KeyBindingMixin {
	@Inject(method = "isPressed", at = @At("HEAD"), cancellable = true)
	private void hkscript$applyForcedState(CallbackInfoReturnable<Boolean> cir) {
		if (ScriptKeys.isForced((KeyBinding) (Object) this)) {
			cir.setReturnValue(true);
		}
	}
}
