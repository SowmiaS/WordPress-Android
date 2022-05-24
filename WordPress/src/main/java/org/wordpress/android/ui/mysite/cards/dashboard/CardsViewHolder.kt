package org.wordpress.android.ui.mysite.cards.dashboard

import android.view.ViewGroup
import org.wordpress.android.R.dimen
import org.wordpress.android.databinding.MySiteDashboardCardsBinding
import org.wordpress.android.ui.mysite.MySiteCardAndItem.Card.DashboardCards
import org.wordpress.android.ui.mysite.MySiteCardAndItemViewHolder
import org.wordpress.android.ui.mysite.cards.dashboard.bloggingprompts.BloggingPromptsCardAnalyticsTracker
import org.wordpress.android.ui.utils.UiHelpers
import org.wordpress.android.util.image.ImageManager
import org.wordpress.android.util.extensions.viewBinding

class CardsViewHolder(
    parentView: ViewGroup,
    imageManager: ImageManager,
    uiHelpers: UiHelpers,
    bloggingPromptsCardAnalyticsTracker: BloggingPromptsCardAnalyticsTracker
) : MySiteCardAndItemViewHolder<MySiteDashboardCardsBinding>(
        parentView.viewBinding(MySiteDashboardCardsBinding::inflate)
) {
    init {
        with(binding.dashboardCards) {
            adapter = CardsAdapter(imageManager, uiHelpers, bloggingPromptsCardAnalyticsTracker)
            addItemDecoration(CardsDecoration(resources.getDimensionPixelSize(dimen.margin_extra_large)))
        }
    }

    fun bind(cards: DashboardCards) = with(binding) {
        (dashboardCards.adapter as CardsAdapter).update(cards.cards)
    }
}
