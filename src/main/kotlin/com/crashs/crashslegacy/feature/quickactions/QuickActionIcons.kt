package com.crashs.crashslegacy.feature.quickactions

import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.ProjectionType
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.client.renderer.CachedOrthoProjectionMatrixBuffer
import net.minecraft.client.renderer.RenderBuffers
import net.minecraft.client.renderer.SubmitNodeStorage
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher
import net.minecraft.client.renderer.item.ItemStackRenderState
import com.mojang.blaze3d.opengl.GlDevice
import com.mojang.blaze3d.opengl.GlStateManager
import com.mojang.blaze3d.opengl.GlTexture
import org.lwjgl.opengl.GL11C
import org.lwjgl.opengl.GL30
import org.lwjgl.system.MemoryStack
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack

/**
 * Renders the real 32x32 item icon through Minecraft's item renderer.
 *
 * The actual draw runs outside the ImGui frame: the page only asks for a texture and receives it on
 * a later frame. [renderPending] is called from [com.crashs.crashslegacy.ui.CrashsImGuiScreen] before
 * the ImGui frame starts, so switching to the offscreen target cannot corrupt ImGui state.
 */
object QuickActionIcons {

    private const val SIZE = 64
    private const val LIGHT = 0xF000F0
    private const val MAX_PER_FRAME = 8

    private data class IconTexture(val target: TextureTarget, val glId: Long)

    private val cache = HashMap<String, IconTexture?>()
    private val pending = LinkedHashSet<String>()
    private val loggedCache = HashSet<String>()
    private var allItems: List<ItemStack>? = null

    private var storage: SubmitNodeStorage? = null
    private var buffers: RenderBuffers? = null
    private var dispatcher: FeatureRenderDispatcher? = null

    fun search(query: String, limit: Int = Int.MAX_VALUE): List<ItemStack> {
        val items = allItems ?: buildList {
            for (item in BuiltInRegistries.ITEM) add(ItemStack(item))
        }.also { allItems = it }
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return items.take(limit)
        return items.asSequence()
            .filter { stack ->
                val id = BuiltInRegistries.ITEM.getKey(stack.item).toString()
                id.lowercase().contains(needle) || stack.hoverName.string.lowercase().contains(needle)
            }
            .take(limit)
            .toList()
    }

    fun displayName(stack: ItemStack): String = stack.hoverName.string

    fun idOf(stack: ItemStack): String = BuiltInRegistries.ITEM.getKey(stack.item).toString()

    /** Returns the cached texture, or queues the item for the next safe render point. */
    fun textureId(itemId: String): Long? {
        if (cache.containsKey(itemId)) {
            val texture = cache[itemId]?.glId
            if (QuickActionDebug.enabled && loggedCache.add(itemId)) {
                QuickActionDebug.log("icon-cache id=$itemId texture=$texture")
            }
            return texture
        }
        if (pending.add(itemId)) {
            QuickActionDebug.log("icon-request id=$itemId pending=${pending.size}")
        }
        return null
    }

    /** Renders a few queued icons. Must be called on the render thread, outside an ImGui frame. */
    fun renderPending() {
        if (pending.isEmpty()) return
        QuickActionDebug.log("icon-pending count=${pending.size}")
        val client = Minecraft.getInstance()
        ensureRenderer(client)
        var rendered = 0
        val iterator = pending.iterator()
        while (iterator.hasNext() && rendered < MAX_PER_FRAME) {
            val itemId = iterator.next()
            iterator.remove()
            if (cache.containsKey(itemId)) continue
            QuickActionDebug.log("icon-render id=$itemId")
            val result = runCatching { renderOne(client, itemId) }
                .onFailure { QuickActionDebug.log("real-icon item=$itemId failed: ${it.message}") }
                .getOrNull()
            cache[itemId] = result
            QuickActionDebug.log("icon-done id=$itemId texture=${result?.glId}")
            rendered++
        }
    }

    private fun ensureRenderer(client: Minecraft) {
        if (dispatcher != null) return
        storage = SubmitNodeStorage()
        buffers = RenderBuffers(1)
        val renderBuffers = buffers ?: return
        dispatcher = FeatureRenderDispatcher(
            storage ?: return,
            client.blockRenderer,
            renderBuffers.bufferSource(),
            client.atlasManager,
            renderBuffers.outlineBufferSource(),
            renderBuffers.crumblingBufferSource(),
            client.font,
        )
    }

    private fun renderOne(client: Minecraft, itemId: String): IconTexture? {
        val id = Identifier.tryParse(itemId) ?: return skip(itemId, "invalid-id")
        val item: Item = BuiltInRegistries.ITEM.getValue(id)
        if (item === net.minecraft.world.item.Items.AIR) return skip(itemId, "air")
        val stack = ItemStack(item)
        if (stack.isEmpty) return skip(itemId, "empty-stack")

        val state = ItemStackRenderState()
        client.itemModelResolver.updateForTopItem(state, stack, contextFor(item), client.level, null, 0)
        if (state.isEmpty) {
            return skip(itemId, "empty-state")
        }

        val target = TextureTarget("crashs-qa-icon", SIZE, SIZE, true)
        val color = target.colorTexture ?: return skip(itemId, "color-null")
        val depth = target.depthTexture ?: return skip(itemId, "depth-null")
        val clearColor = if (QuickActionDebug.enabled) 0xFFFF00FF.toInt() else 0
        QuickActionDebug.log(
            "icon-start item=$itemId target=${target.width}x${target.height} " +
                "stateEmpty=${state.isEmpty} blockLight=${state.usesBlockLight()} " +
                "clear=${if (QuickActionDebug.enabled) "magenta" else "transparent"}",
        )
        val encoder = RenderSystem.getDevice().createCommandEncoder()
        encoder.clearColorAndDepthTextures(color, clearColor, depth, 1.0)

        val oldColor = RenderSystem.outputColorTextureOverride
        val oldDepth = RenderSystem.outputDepthTextureOverride
        val modelView = RenderSystem.getModelViewStack()
        val projection = CachedOrthoProjectionMatrixBuffer("crashs-qa-icon", -1000f, 1000f, true)
        val pose = PoseStack()

        try {
            RenderSystem.backupProjectionMatrix()
            RenderSystem.setProjectionMatrix(projection.getBuffer(SIZE.toFloat(), SIZE.toFloat()), ProjectionType.ORTHOGRAPHIC)
            modelView.pushMatrix()
            modelView.identity()
            RenderSystem.outputColorTextureOverride = target.colorTextureView
            RenderSystem.outputDepthTextureOverride = target.depthTextureView

            val glDevice = RenderSystem.getDevice() as? GlDevice
            val glColor = color as? GlTexture
            if (glDevice != null && glColor != null) {
                val fbo = glColor.getFbo(glDevice.directStateAccess(), depth)
                GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo)
                GlStateManager._viewport(0, 0, SIZE, SIZE)
                GlStateManager._disableScissorTest()
                QuickActionDebug.log("icon-fbo item=$itemId fbo=$fbo")
            }

            pose.translate(SIZE / 2f, SIZE / 2f, 0f)
            pose.scale(SIZE.toFloat(), -SIZE.toFloat(), SIZE.toFloat())

            val lighting = client.gameRenderer.lighting
            lighting.setupFor(
                if (state.usesBlockLight()) Lighting.Entry.ITEMS_3D else Lighting.Entry.ITEMS_FLAT,
            )
            state.submit(pose, storage ?: return null, LIGHT, OverlayTexture.NO_OVERLAY, 0)
            dispatcher?.renderAllFeatures()
            buffers?.bufferSource()?.endBatch()
            buffers?.outlineBufferSource()?.endOutlineBatch()
            buffers?.crumblingBufferSource()?.endBatch()
            storage?.clear()
            GL11C.glFinish()
            QuickActionDebug.log("icon-pixels item=$itemId ${readPixels(color as? GlTexture, clearColor)}")
        } finally {
            RenderSystem.outputColorTextureOverride = oldColor
            RenderSystem.outputDepthTextureOverride = oldDepth
            modelView.popMatrix()
            RenderSystem.restoreProjectionMatrix()
            projection.close()
            rebindMainTarget(client)
        }

        val glTexture = target.colorTexture as? GlTexture ?: run {
            target.destroyBuffers()
            return null
        }
        val iconId = glTexture.glId().toLong()
        QuickActionDebug.log("real-icon item=$itemId texture=$iconId")
        return IconTexture(target, iconId)
    }

    private fun skip(itemId: String, reason: String): IconTexture? {
        QuickActionDebug.log("icon-skip id=$itemId reason=$reason")
        return null
    }

    private fun contextFor(item: Item): ItemDisplayContext =
        when (item) {
            is net.minecraft.world.item.ShieldItem,
            is net.minecraft.world.item.TridentItem,
            is net.minecraft.world.item.BannerItem,
            is net.minecraft.world.item.SpyglassItem,
            -> ItemDisplayContext.FIXED

            else -> ItemDisplayContext.GUI
        }



    private fun readPixels(texture: GlTexture?, clearColor: Int): String {
        if (texture == null) return "texture=null"
        val stack = MemoryStack.stackPush()
        return try {
            val buffer = stack.malloc(SIZE * SIZE * 4)
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, texture.glId())
            GL11C.glGetTexImage(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, buffer)
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, 0)
            val clearR = (clearColor shr 16) and 0xFF
            val clearG = (clearColor shr 8) and 0xFF
            val clearB = clearColor and 0xFF
            var nonClear = 0
            var opaque = 0
            var maxAlpha = 0
            for (i in 0 until SIZE * SIZE) {
                val r = buffer.get(i * 4).toInt() and 0xFF
                val g = buffer.get(i * 4 + 1).toInt() and 0xFF
                val b = buffer.get(i * 4 + 2).toInt() and 0xFF
                val a = buffer.get(i * 4 + 3).toInt() and 0xFF
                if (a > maxAlpha) maxAlpha = a
                if (a > 0) opaque++
                if (a > 0 && (r != clearR || g != clearG || b != clearB)) nonClear++
            }
            "opaque=$opaque nonClear=$nonClear maxAlpha=$maxAlpha"
        } catch (t: Throwable) {
            "readFailed=${t.message}"
        } finally {
            stack.pop()
        }
    }

    private fun rebindMainTarget(client: Minecraft) {
        runCatching {
            val target = client.mainRenderTarget
            val color = target.colorTexture as? GlTexture ?: return
            val device = RenderSystem.getDevice() as? GlDevice ?: return
            val fbo = color.getFbo(device.directStateAccess(), target.depthTexture)
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo)
            GlStateManager._viewport(0, 0, target.width, target.height)
            GlStateManager._disableScissorTest()
        }
    }

    fun clearCache() {
        for (entry in cache.values) runCatching { entry?.target?.destroyBuffers() }
        cache.clear()
        pending.clear()
    }
}
