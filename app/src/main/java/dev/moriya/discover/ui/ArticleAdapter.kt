package dev.moriya.discover.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import dev.moriya.discover.data.Article
import dev.moriya.discover.data.SlotType
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Shared row for Feed / Inbox / Source page (design §2, §6), built
 * programmatically like the sibling apps: small thumbnail on the left,
 * title (2 lines) and one meta line (source · elapsed · topic) on the
 * right. Not a large card.
 */
class ArticleAdapter(
    private val onTap: (Article) -> Unit,
    private val onLongPress: (Article) -> Unit,
    private val onSourceTap: (Article) -> Unit,
    private val onBound: ((Article) -> Unit)? = null,
) : RecyclerView.Adapter<ArticleAdapter.VH>() {

    val items = mutableListOf<Article>()

    fun submit(list: List<Article>) {
        items.clear(); items.addAll(list); notifyDataSetChanged()
    }

    fun remove(article: Article): Int {
        val idx = items.indexOfFirst { it.id == article.id }
        if (idx >= 0) { items.removeAt(idx); notifyItemRemoved(idx) }
        return idx
    }

    fun insert(article: Article, index: Int) {
        val i = index.coerceIn(0, items.size)
        items.add(i, article); notifyItemInserted(i)
    }

    class VH(val root: LinearLayout, val thumb: ImageView, val title: TextView,
             val meta: TextView, val sourceLink: TextView) : RecyclerView.ViewHolder(root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context
        val thumb = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(ctx.dp(96), ctx.dp(68))
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        val title = ctx.textView(sizeSp = 15f, bold = true, maxLines = 2)
        val meta = ctx.textView(sizeSp = 12f, maxLines = 1).apply {
            setPadding(0, ctx.dp(4), 0, 0)
        }
        val sourceLink = ctx.textView(sizeSp = 12f, color = 0xFF1565C0.toInt()).apply {
            setPadding(0, ctx.dp(2), 0, 0)
        }
        val texts = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(ctx.dp(12), 0, 0, 0)
            addView(title); addView(meta); addView(sourceLink)
        }
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(ctx.dp(12), ctx.dp(10), ctx.dp(12), ctx.dp(10))
            addView(thumb); addView(texts)
        }
        return VH(root, thumb, title, meta, sourceLink)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val a = items[position]
        holder.title.text = a.title
        holder.meta.text = listOfNotNull(
            a.sourceName, TimeFmt.elapsed(a.publishedAtEpochMs),
            a.topic.ifEmpty { null },
            when (a.slotType) {
                SlotType.EXPLORE_NEAR, SlotType.EXPLORE_FAR -> "新しい話題"
                else -> null
            },
        ).joinToString(" ・ ")
        holder.sourceLink.text = a.sourceName
        holder.thumb.setImageBitmap(null)
        holder.thumb.setBackgroundColor(thumbColor(a.sourceId))
        a.thumbnailUrl?.let { ThumbLoader.load(it, holder.thumb) }
        holder.root.setOnClickListener { onTap(a) }
        holder.root.setOnLongClickListener { onLongPress(a); true }
        holder.sourceLink.setOnClickListener { onSourceTap(a) }
        onBound?.invoke(a)
    }

    private fun thumbColor(sourceId: String): Int {
        val palette = intArrayOf(0xFFD7CCC8.toInt(), 0xFFC5CAE9.toInt(), 0xFFC8E6C9.toInt(),
            0xFFFFE0B2.toInt(), 0xFFB2DFDB.toInt(), 0xFFF8BBD0.toInt())
        return palette[(sourceId.hashCode() and Int.MAX_VALUE) % palette.size]
    }
}

object TimeFmt {
    fun elapsed(epochMs: Long): String {
        val diffMin = ((System.currentTimeMillis() - epochMs) / 60_000).coerceAtLeast(0)
        return when {
            diffMin < 60 -> "${diffMin}分前"
            diffMin < 60 * 24 -> "${diffMin / 60}時間前"
            else -> "${diffMin / (60 * 24)}日前"
        }
    }

    fun dateTime(epochMs: Long): String =
        java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.JAPAN)
            .format(java.util.Date(epochMs))
}

/** Minimal thumbnail loader (memory cache only in M1). */
object ThumbLoader {
    private val cache = LruCache<String, Bitmap>(64)
    private val pool = Executors.newFixedThreadPool(3)

    fun load(url: String, target: ImageView) {
        cache.get(url)?.let { target.setImageBitmap(it); return }
        target.tag = url
        pool.execute {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 4000; conn.readTimeout = 4000
                val bmp = conn.inputStream.use { BitmapFactory.decodeStream(it) } ?: return@execute
                cache.put(url, bmp)
                target.post { if (target.tag == url) target.setImageBitmap(bmp) }
            } catch (t: Throwable) { /* placeholder stays */ }
        }
    }
}
