package org.wordpress.android.viewmodel.activitylog

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.model.activity.ActivityLogModel.ActivityActor
import org.wordpress.android.fluxc.store.ActivityLogStore
import org.wordpress.android.fluxc.tools.FormattableRange
import org.wordpress.android.ui.activitylog.detail.ActivityLogDetailModel
import org.wordpress.android.ui.activitylog.detail.ActivityLogDetailNavigationEvents
import org.wordpress.android.util.AppLog
import org.wordpress.android.util.AppLog.T.ACTIVITY_LOG
import org.wordpress.android.util.toFormattedDateString
import org.wordpress.android.util.toFormattedTimeString
import org.wordpress.android.viewmodel.Event
import org.wordpress.android.viewmodel.SingleLiveEvent
import javax.inject.Inject

const val ACTIVITY_LOG_ID_KEY: String = "activity_log_id_key"
const val ACTIVITY_LOG_ARE_BUTTONS_VISIBLE_KEY: String = "activity_log_are_buttons_visible_key"

class ActivityLogDetailViewModel
@Inject constructor(
    val dispatcher: Dispatcher,
    private val activityLogStore: ActivityLogStore
) : ViewModel() {
    lateinit var site: SiteModel
    lateinit var activityLogId: String
    var areButtonsVisible = false
    var isRestoreHidden = false

    private val _navigationEvents = MutableLiveData<Event<ActivityLogDetailNavigationEvents>>()
    val navigationEvents: LiveData<Event<ActivityLogDetailNavigationEvents>>
        get() = _navigationEvents

    private val _handleFormattableRangeClick = SingleLiveEvent<FormattableRange>()
    val handleFormattableRangeClick: LiveData<FormattableRange>
        get() = _handleFormattableRangeClick

    private val _item = MutableLiveData<ActivityLogDetailModel>()
    val activityLogItem: LiveData<ActivityLogDetailModel>
        get() = _item

    private val _restoreVisible = MutableLiveData<Boolean>()
    val restoreVisible: LiveData<Boolean>
        get() = _restoreVisible

    private val _downloadBackupVisible = MutableLiveData<Boolean>()
    val downloadBackupVisible: LiveData<Boolean>
        get() = _downloadBackupVisible

    fun start(
        site: SiteModel,
        activityLogId: String,
        areButtonsVisible: Boolean,
        isRestoreHidden: Boolean,
    ) {
        this.site = site
        this.activityLogId = activityLogId
        this.areButtonsVisible = areButtonsVisible
        this.isRestoreHidden = isRestoreHidden

        _restoreVisible.value = areButtonsVisible && !isRestoreHidden
        _downloadBackupVisible.value = areButtonsVisible

        if (activityLogId != _item.value?.activityID) {
            _item.value = activityLogStore
                    .getActivityLogForSite(site)
                    .find { it.activityID == activityLogId }
                    ?.let {
                        ActivityLogDetailModel(
                                activityID = it.activityID,
                                rewindId = it.rewindID,
                                actorIconUrl = it.actor?.avatarURL,
                                showJetpackIcon = it.actor?.showJetpackIcon(),
                                isRewindButtonVisible = it.rewindable ?: false,
                                actorName = it.actor?.displayName,
                                actorRole = it.actor?.role,
                                content = it.content,
                                summary = it.summary,
                                createdDate = it.published.toFormattedDateString(),
                                createdTime = it.published.toFormattedTimeString()
                        )
                    }
        }
    }

    fun onRangeClicked(range: FormattableRange) {
        _handleFormattableRangeClick.value = range
    }

    fun onRestoreClicked(model: ActivityLogDetailModel) {
        if (model.rewindId != null) {
            _navigationEvents.value = Event(ActivityLogDetailNavigationEvents.ShowRestore(model))
        } else {
            AppLog.e(ACTIVITY_LOG, "Trying to restore activity without rewind ID")
        }
    }

    fun onDownloadBackupClicked(model: ActivityLogDetailModel) {
        if (model.rewindId != null) {
            _navigationEvents.value = Event(ActivityLogDetailNavigationEvents.ShowBackupDownload(model))
        } else {
            AppLog.e(ACTIVITY_LOG, "Trying to download backup activity without rewind ID")
        }
    }

    private fun ActivityActor.showJetpackIcon(): Boolean {
        return displayName == "Jetpack" && type == "Application" ||
                displayName == "Happiness Engineer" && type == "Happiness Engineer"
    }
}
