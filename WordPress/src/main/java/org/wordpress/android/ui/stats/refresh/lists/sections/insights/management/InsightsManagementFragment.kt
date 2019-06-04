package org.wordpress.android.ui.stats.refresh.lists.sections.insights.management

import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import dagger.android.support.DaggerFragment
import kotlinx.android.synthetic.main.insights_management_fragment.*
import javax.inject.Inject
import android.animation.LayoutTransition
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.wordpress.android.R
import org.wordpress.android.ui.stats.refresh.lists.sections.insights.management.InsightsManagementViewModel.InsightModel

class InsightsManagementFragment : DaggerFragment() {
    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
    private lateinit var viewModel: InsightsManagementViewModel
    private lateinit var addedInsightsTouchHelper: ItemTouchHelper

    private var menu: Menu? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        setHasOptionsMenu(true)
        return inflater.inflate(R.layout.insights_management_fragment, container, false)
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        super.onCreateOptionsMenu(menu, inflater)

        inflater?.inflate(R.menu.menu_insights_management, menu)
        this.menu = menu

        initializeViews()
        initializeViewModels(requireActivity())

        enableAnimations()
    }

    private fun enableAnimations() {
        viewModel.launch {
            delay(500)
            val transition = LayoutTransition()
            transition.disableTransitionType(LayoutTransition.DISAPPEARING)
            transition.disableTransitionType(LayoutTransition.APPEARING)
            transition.enableTransitionType(LayoutTransition.CHANGING)
            insightsManagementContainer.layoutTransition = transition
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.save_insights) {
            viewModel.onSaveInsights()
        }
        return true
    }

    private fun initializeViews() {
        removedInsights.layoutManager = LinearLayoutManager(requireActivity(), androidx.recyclerview.widget.LinearLayoutManager.VERTICAL, false)
        addedInsights.layoutManager = LinearLayoutManager(requireActivity(), androidx.recyclerview.widget.LinearLayoutManager.VERTICAL, false)
    }

    private fun initializeViewModels(activity: androidx.fragment.app.FragmentActivity) {
        viewModel = ViewModelProviders.of(activity, viewModelFactory).get(InsightsManagementViewModel::class.java)
        viewModel.start()

        setupObservers()
    }

    private fun setupObservers() {
        viewModel.removedInsights.observe(this, Observer {
            it?.let { items ->
                updateRemovedInsights(items)

                if (items.isEmpty()) {
                    addInsightsHeader.visibility = View.GONE
                } else {
                    addInsightsHeader.visibility = View.VISIBLE
                }
            }
        })

        viewModel.addedInsights.observe(this, Observer {
            it?.let { items ->
                updateAddedInsights(items)

                if (items.isEmpty()) {
                    addedInsightsInfo.visibility = View.GONE
                } else {
                    addedInsightsInfo.visibility = View.VISIBLE
                }
            }
        })

        viewModel.closeInsightsManagement.observe(this, Observer {
            requireActivity().finish()
        })

        viewModel.isMenuVisible.observe(this, Observer { isMenuVisible ->
            isMenuVisible?.let {
                menu?.findItem(R.id.save_insights)?.isVisible = isMenuVisible
            }
        })
    }

    private fun updateRemovedInsights(insights: List<InsightModel>) {
        var adapter = removedInsights.adapter as? InsightsManagementAdapter
        if (adapter == null) {
            adapter = InsightsManagementAdapter(
                    { item -> viewModel.onItemButtonClicked(item) },
                    { viewHolder -> addedInsightsTouchHelper.startDrag(viewHolder) },
                    { list -> viewModel.onAddedInsightsReordered(list) }
            )
            removedInsights.adapter = adapter
        }
        adapter.update(insights)
    }

    private fun updateAddedInsights(insights: List<InsightModel>) {
        var adapter = addedInsights.adapter as? InsightsManagementAdapter
        if (adapter == null) {
            adapter = InsightsManagementAdapter(
                    { item -> viewModel.onItemButtonClicked(item) },
                    { viewHolder -> addedInsightsTouchHelper.startDrag(viewHolder) },
                    { list -> viewModel.onAddedInsightsReordered(list) }
            )
            addedInsights.adapter = adapter

            val callback = ItemTouchHelperCallback(adapter)
            addedInsightsTouchHelper = ItemTouchHelper(callback)
            addedInsightsTouchHelper.attachToRecyclerView(addedInsights)
        }
        adapter.update(insights)
    }
}
