package org.wordpress.android.ui.stats.refresh

import android.content.Context
import android.support.annotation.DrawableRes
import android.support.annotation.StringRes
import org.wordpress.android.ui.stats.refresh.BlockListItem.Type.BAR_CHART
import org.wordpress.android.ui.stats.refresh.BlockListItem.Type.COLUMNS
import org.wordpress.android.ui.stats.refresh.BlockListItem.Type.EMPTY
import org.wordpress.android.ui.stats.refresh.BlockListItem.Type.EXPANDABLE_ITEM
import org.wordpress.android.ui.stats.refresh.BlockListItem.Type.INFO
import org.wordpress.android.ui.stats.refresh.BlockListItem.Type.ITEM
import org.wordpress.android.ui.stats.refresh.BlockListItem.Type.LABEL
import org.wordpress.android.ui.stats.refresh.BlockListItem.Type.LINK
import org.wordpress.android.ui.stats.refresh.BlockListItem.Type.LIST_ITEM
import org.wordpress.android.ui.stats.refresh.BlockListItem.Type.TABS
import org.wordpress.android.ui.stats.refresh.BlockListItem.Type.TEXT
import org.wordpress.android.ui.stats.refresh.BlockListItem.Type.TITLE
import org.wordpress.android.ui.stats.refresh.BlockListItem.Type.USER_ITEM
import org.wordpress.android.util.ImageResource
import org.wordpress.android.util.TextResource

sealed class BlockListItem(val type: Type) {
    enum class Type {
        TITLE,
        ITEM,
        USER_ITEM,
        LIST_ITEM,
        INFO,
        EMPTY,
        TEXT,
        COLUMNS,
        LINK,
        BAR_CHART,
        TABS,
        LABEL,
        EXPANDABLE_ITEM
    }

    data class Title(@StringRes val text: Int) : BlockListItem(TITLE)
    data class Item(
        val icon: ImageResource,
        val text: TextResource,
        val value: TextResource,
        val showDivider: Boolean = true,
        val clickAction: (() -> Unit)? = null
    ) : BlockListItem(ITEM) {
        constructor(
            @DrawableRes icon: Int,
            @StringRes text: Int,
            value: String,
            showDivider: Boolean,
            clickAction: (() -> Unit)? = null
        ) : this(
                ImageResource(icon),
                TextResource(text),
                TextResource(value),
                showDivider,
                clickAction
        )

        constructor(
            @DrawableRes icon: Int,
            text: String,
            value: String,
            showDivider: Boolean,
            clickAction: (() -> Unit)? = null
        ) : this(
                ImageResource(icon),
                TextResource(text),
                TextResource(value),
                showDivider,
                clickAction
        )
    }

    data class UserItem(
        val avatarUrl: String,
        val text: String,
        val value: String,
        val showDivider: Boolean = true
    ) : BlockListItem(USER_ITEM)

    data class ListItem(
        val text: String,
        val value: String,
        val showDivider: Boolean = true
    ) : BlockListItem(LIST_ITEM)

    data class Information(val text: String) : BlockListItem(INFO)

    data class Text(val text: String, val links: List<Clickable>? = null) : BlockListItem(TEXT) {
        data class Clickable(val link: String, val action: (Context) -> Unit)
    }

    data class Columns(val headers: List<Int>, val values: List<String>) : BlockListItem(COLUMNS)
    data class Link(@DrawableRes val icon: Int? = null, @StringRes val text: Int, val action: () -> Unit) :
            BlockListItem(LINK)

    data class BarChartItem(val entries: List<Pair<String, Int>>) : BlockListItem(BAR_CHART)
    data class TabsItem(val tabs: List<Tab>) : BlockListItem(TABS) {
        data class Tab(@StringRes val title: Int, val items: List<BlockListItem>)
    }

    data class Label(@StringRes val leftLabel: Int, @StringRes val rightLabel: Int) : BlockListItem(LABEL)
    data class ExpandableItem(
        val header: Item,
        val expandedItems: List<BlockListItem>,
        var isExpanded: Boolean = false
    ) : BlockListItem(
            EXPANDABLE_ITEM
    )

    object Empty : BlockListItem(EMPTY)
}
