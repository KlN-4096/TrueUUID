package cn.alini.trueuuid.mixin.client;

import cn.alini.trueuuid.net.NetIds;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User; // official 映射
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;
import net.minecraft.network.protocol.login.ServerboundCustomQueryPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Mixin(ClientHandshakePacketListenerImpl.class)
public abstract class ClientHandshakeMixin {
    @Shadow private Connection connection;

    @Inject(method = "handleCustomQuery", at = @At("HEAD"), cancellable = true)
    private void trueuuid$onCustomQuery(ClientboundCustomQueryPacket packet, CallbackInfo ci) {
        if (!NetIds.AUTH.equals(packet.getIdentifier())) return;

        FriendlyByteBuf buf = packet.getData();
        String serverId = buf.readUtf();
        long serverTimeoutMs = -1L;
        try {
            if (buf.readableBytes() >= Long.BYTES) {
                serverTimeoutMs = buf.readLong();
            }
        } catch (Throwable ignored) {}

        Minecraft mc = Minecraft.getInstance();
        int txId = packet.getTransactionId();

        CompletableFuture
                .supplyAsync(() -> {
                    try {
                        User user = mc.getUser();
                        var profile = user.getGameProfile();
                        String token = user.getAccessToken();

                        // 令牌只在本地使用
                        mc.getMinecraftSessionService().joinServer(profile, token, serverId);
                        return true;
                    } catch (Throwable t) {
                        return false;
                    }
                })
                // 防止 joinServer 过慢导致服务端超时并进入压缩阶段，之后再回包会引发协议/压缩错位
                .completeOnTimeout(false, Math.max(1000L, serverTimeoutMs > 0 ? (serverTimeoutMs - 500L) : 8000L), TimeUnit.MILLISECONDS)
                .thenAccept(ok -> {
                    // 仅在仍处于 LOGIN 握手阶段时回包；避免“晚到的 LOGIN 包”打进 PLAY/压缩阶段导致解码异常
                    try {
                        if (this.connection.getPacketListener() != (Object) this) return;
                    } catch (Throwable ignored) {
                        // 若无法获取 listener，则仍尝试回包（保持兼容）
                    }

                    FriendlyByteBuf resp = new FriendlyByteBuf(Unpooled.buffer());
                    resp.writeBoolean(ok);
                    this.connection.send(new ServerboundCustomQueryPacket(txId, resp));
                });

        ci.cancel();
    }

}
