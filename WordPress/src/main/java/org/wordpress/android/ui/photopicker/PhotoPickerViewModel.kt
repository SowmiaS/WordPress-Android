package org.wordpress.android.ui.photopicker

import android.Manifest.permission
import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import org.wordpress.android.R
import org.wordpress.android.analytics.AnalyticsTracker
import org.wordpress.android.analytics.AnalyticsTracker.Stat
import org.wordpress.android.analytics.AnalyticsTracker.Stat.MEDIA_PICKER_OPEN_CAPTURE_MEDIA
import org.wordpress.android.analytics.AnalyticsTracker.Stat.MEDIA_PICKER_OPEN_DEVICE_LIBRARY
import org.wordpress.android.analytics.AnalyticsTracker.Stat.MEDIA_PICKER_OPEN_WP_MEDIA
import org.wordpress.android.analytics.AnalyticsTracker.Stat.MEDIA_PICKER_OPEN_WP_STORIES_CAPTURE
import org.wordpress.android.analytics.AnalyticsTracker.Stat.MEDIA_PICKER_PREVIEW_OPENED
import org.wordpress.android.analytics.AnalyticsTracker.Stat.MEDIA_PICKER_RECENT_MEDIA_SELECTED
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.modules.BG_THREAD
import org.wordpress.android.modules.UI_THREAD
import org.wordpress.android.ui.media.MediaBrowserType
import org.wordpress.android.ui.media.MediaBrowserType.AZTEC_EDITOR_PICKER
import org.wordpress.android.ui.media.MediaBrowserType.GRAVATAR_IMAGE_PICKER
import org.wordpress.android.ui.media.MediaBrowserType.SITE_ICON_PICKER
import org.wordpress.android.ui.photopicker.PhotoPickerFragment.PhotoPickerIcon
import org.wordpress.android.ui.photopicker.PhotoPickerFragment.PhotoPickerIcon.ANDROID_CAPTURE_PHOTO
import org.wordpress.android.ui.photopicker.PhotoPickerFragment.PhotoPickerIcon.ANDROID_CAPTURE_VIDEO
import org.wordpress.android.ui.photopicker.PhotoPickerFragment.PhotoPickerIcon.ANDROID_CHOOSE_PHOTO
import org.wordpress.android.ui.photopicker.PhotoPickerFragment.PhotoPickerIcon.ANDROID_CHOOSE_PHOTO_OR_VIDEO
import org.wordpress.android.ui.photopicker.PhotoPickerFragment.PhotoPickerIcon.ANDROID_CHOOSE_VIDEO
import org.wordpress.android.ui.photopicker.PhotoPickerFragment.PhotoPickerIcon.GIF
import org.wordpress.android.ui.photopicker.PhotoPickerFragment.PhotoPickerIcon.STOCK_MEDIA
import org.wordpress.android.ui.photopicker.PhotoPickerFragment.PhotoPickerIcon.WP_MEDIA
import org.wordpress.android.ui.photopicker.PhotoPickerFragment.PhotoPickerIcon.WP_STORIES_CAPTURE
import org.wordpress.android.ui.photopicker.PhotoPickerUiItem.ClickAction
import org.wordpress.android.ui.photopicker.PhotoPickerUiItem.ToggleAction
import org.wordpress.android.ui.photopicker.PhotoPickerViewModel.BottomBarUiModel.BottomBar.INSERT_EDIT
import org.wordpress.android.ui.photopicker.PhotoPickerViewModel.BottomBarUiModel.BottomBar.MEDIA_SOURCE
import org.wordpress.android.ui.photopicker.PhotoPickerViewModel.BottomBarUiModel.BottomBar.NONE
import org.wordpress.android.ui.photopicker.PhotoPickerViewModel.PopupMenuUiModel.PopupMenuItem
import org.wordpress.android.ui.photopicker.PhotoPickerViewModel.SoftAskViewUiModel.Show
import org.wordpress.android.ui.utils.UiString
import org.wordpress.android.ui.utils.UiString.UiStringRes
import org.wordpress.android.ui.utils.UiString.UiStringText
import org.wordpress.android.util.AppLog
import org.wordpress.android.util.AppLog.T.MEDIA
import org.wordpress.android.util.MediaUtils
import org.wordpress.android.util.UriWrapper
import org.wordpress.android.util.ViewWrapper
import org.wordpress.android.util.WPPermissionUtils
import org.wordpress.android.util.analytics.AnalyticsTrackerWrapper
import org.wordpress.android.util.analytics.AnalyticsUtilsWrapper
import org.wordpress.android.util.config.TenorFeatureConfig
import org.wordpress.android.util.distinct
import org.wordpress.android.util.map
import org.wordpress.android.util.merge
import org.wordpress.android.viewmodel.Event
import org.wordpress.android.viewmodel.ScopedViewModel
import java.util.HashMap
import javax.inject.Inject
import javax.inject.Named

class PhotoPickerViewModel @Inject constructor(
    @Named(UI_THREAD) private val mainDispatcher: CoroutineDispatcher,
    @Named(BG_THREAD) private val bgDispatcher: CoroutineDispatcher,
    private val deviceMediaListBuilder: DeviceMediaListBuilder,
    private val analyticsUtilsWrapper: AnalyticsUtilsWrapper,
    private val analyticsTrackerWrapper: AnalyticsTrackerWrapper,
    private val permissionsHandler: PermissionsHandler,
    private val tenorFeatureConfig: TenorFeatureConfig,
    private val context: Context
) : ScopedViewModel(mainDispatcher) {
    private val _navigateToPreview = MutableLiveData<Event<UriWrapper>>()
    private val _onInsert = MutableLiveData<Event<List<UriWrapper>>>()
    private val _showPopupMenu = MutableLiveData<Event<PopupMenuUiModel>>()
    private val _data = MutableLiveData<List<PhotoPickerItem>>()
    private val _selectedIds = MutableLiveData<List<Long>>()
    private val _onIconClicked = MutableLiveData<Event<IconClickEvent>>()
    private val _onPermissionsRequested = MutableLiveData<Event<PermissionsRequested>>()
    private val _softAskViewModel = MutableLiveData<SoftAskViewUiModel>()

    val onNavigateToPreview: LiveData<Event<UriWrapper>> = _navigateToPreview
    val onInsert: LiveData<Event<List<UriWrapper>>> = _onInsert
    val onIconClicked: LiveData<Event<IconClickEvent>> = _onIconClicked

    val onShowActionMode: LiveData<Boolean> = _selectedIds.map { selectedIds ->
        !selectedIds.isNullOrEmpty()
    }
    val onShowPopupMenu: LiveData<Event<PopupMenuUiModel>> = _showPopupMenu
    val onPermissionsRequested: LiveData<Event<PermissionsRequested>> = _onPermissionsRequested

    val actionModeUiModel: LiveData<ActionModeUiModel> = _selectedIds.map { selectedIds ->
        buildActionModeUiModel(selectedIds)
    }

    val selectedIds: LiveData<List<Long>> = _selectedIds
    private val data: LiveData<PhotoListUiModel> = merge(
            _data.distinct(),
            _selectedIds.distinct()
    ) { data, selectedIds ->
        buildPhotoPickerUiModel(data, selectedIds)
    }

    val uiState: LiveData<PhotoPickerUiState> = merge(
            data,
            _softAskViewModel
    ) { photoListUiModel, softAskViewUiModel ->
        PhotoPickerUiState(
                photoListUiModel,
                buildBottomBar(
                        photoListUiModel?.count ?: 0,
                        photoListUiModel?.isVideoSelected ?: false,
                        softAskViewUiModel is Show
                ),
                softAskViewUiModel,
                FabUiModel(browserType.isWPStoriesPicker) {
                    clickIcon(WP_STORIES_CAPTURE)
                })
    }

    var lastTappedIcon: PhotoPickerIcon? = null
    private lateinit var browserType: MediaBrowserType
    private var site: SiteModel? = null

    private fun buildPhotoPickerUiModel(
        data: List<PhotoPickerItem>?,
        selectedIds: List<Long>?
    ): PhotoListUiModel? {
        var isVideoSelected = false
        return if (data != null) {
            val uiItems = data.map {
                if (selectedIds != null && selectedIds.contains(it.id)) {
                    isVideoSelected = isVideoSelected || it.isVideo
                    PhotoPickerUiItem(
                            id = it.id,
                            uri = it.uri,
                            isVideo = it.isVideo,
                            isSelected = true,
                            selectedOrder = if (browserType.canMultiselect()) selectedIds.indexOf(it.id) + 1 else null,
                            showOrderCounter = browserType.canMultiselect(),
                            toggleAction = ToggleAction(it.id, browserType.canMultiselect(), this::toggleItem),
                            clickAction = ClickAction(it.id, it.uri, it.isVideo, this::clickItem)
                    )
                } else {
                    PhotoPickerUiItem(
                            id = it.id,
                            uri = it.uri,
                            isVideo = it.isVideo,
                            isSelected = false,
                            selectedOrder = null,
                            showOrderCounter = browserType.canMultiselect(),
                            toggleAction = ToggleAction(it.id, browserType.canMultiselect(), this::toggleItem),
                            clickAction = ClickAction(it.id, it.uri, it.isVideo, this::clickItem)
                    )
                }
            }
            val count = selectedIds?.size ?: 0
            PhotoListUiModel(
                    uiItems,
                    count,
                    isVideoSelected
            )
        } else {
            null
        }
    }

    private fun buildActionModeUiModel(
        selectedIds: List<Long>?
    ): ActionModeUiModel? {
        val numSelected = selectedIds?.size ?: 0
        val title: UiString? = when {
            numSelected == 0 -> null
            browserType.canMultiselect() -> {
                UiStringText(String.format(context.resources.getString(R.string.cab_selected), numSelected))
            }
            else -> {
                if (browserType.isImagePicker && browserType.isVideoPicker) {
                    UiStringRes(R.string.photo_picker_use_media)
                } else if (browserType.isVideoPicker) {
                    UiStringRes(R.string.photo_picker_use_video)
                } else {
                    UiStringRes(R.string.photo_picker_use_photo)
                }
            }
        }
        return ActionModeUiModel(title, showConfirmAction = !browserType.isGutenbergPicker)
    }

    private fun buildBottomBar(
        count: Int,
        isVideoSelected: Boolean,
        showSoftAskViewModel: Boolean
    ): BottomBarUiModel {
        val defaultBottomBar = when {
            showSoftAskViewModel -> NONE
            count <= 0 -> MEDIA_SOURCE
            browserType.isGutenbergPicker -> INSERT_EDIT
            else -> NONE
        }

        val insertEditTextBarVisible = count != 0 && browserType.isGutenbergPicker && !isVideoSelected
        val showCamera = !browserType.isGutenbergPicker && !browserType.isWPStoriesPicker
        return BottomBarUiModel(
                type = defaultBottomBar,
                insertEditTextBarVisible = insertEditTextBarVisible,
                hideMediaBottomBarInPortrait = browserType == AZTEC_EDITOR_PICKER,
                showCameraButton = showCamera,
                showWPMediaIcon = site != null && !browserType.isGutenbergPicker,
                canShowInsertEditBottomBar = browserType.isGutenbergPicker,
                onIconPickerClicked = { v ->
                    if (browserType == GRAVATAR_IMAGE_PICKER || browserType == SITE_ICON_PICKER) {
                        clickIcon(ANDROID_CHOOSE_PHOTO)
                    } else {
                        performActionOrShowPopup(v)
                    }
                }
        )
    }

    fun refreshData(forceReload: Boolean) {
        if (!permissionsHandler.hasStoragePermission()) {
            return
        }
        launch(bgDispatcher) {
            val result = deviceMediaListBuilder.buildDeviceMedia(browserType)
            val currentItems = _data.value ?: listOf()
            if (forceReload || currentItems != result) {
                _data.postValue(result)
            }
        }
    }

    fun clearSelection() {
        if (!_selectedIds.value.isNullOrEmpty()) {
            _selectedIds.postValue(listOf())
        }
    }

    fun start(
        selectedIds: List<Long>?,
        browserType: MediaBrowserType,
        lastTappedIcon: PhotoPickerIcon?,
        site: SiteModel?
    ) {
        selectedIds?.let {
            _selectedIds.value = selectedIds
        }
        this.browserType = browserType
        this.lastTappedIcon = lastTappedIcon
        this.site = site
    }

    fun numSelected(): Int {
        return _selectedIds.value?.size ?: 0
    }

    fun selectedURIs(): List<UriWrapper> {
        return data.value?.items?.mapNotNull { if (it.isSelected) it.uri else null } ?: listOf()
    }

    private fun toggleItem(id: Long, canMultiselect: Boolean) {
        val updatedIds = _selectedIds.value?.toMutableList() ?: mutableListOf()
        if (updatedIds.contains(id)) {
            updatedIds.remove(id)
        } else {
            if (updatedIds.isNotEmpty() && !canMultiselect) {
                updatedIds.clear()
            }
            updatedIds.add(id)
        }
        _selectedIds.postValue(updatedIds)
    }

    private fun clickItem(id: Long, uri: UriWrapper?, isVideo: Boolean) {
        trackOpenPreviewScreenEvent(id, uri, isVideo)
        uri?.let {
            _navigateToPreview.postValue(Event(it))
        }
    }

    private fun trackOpenPreviewScreenEvent(id: Long, uri: UriWrapper?, isVideo: Boolean) {
        launch(bgDispatcher) {
            val properties = analyticsUtilsWrapper.getMediaProperties(
                    isVideo,
                    uri,
                    null
            )
            properties["is_video"] = isVideo
            analyticsTrackerWrapper.track(MEDIA_PICKER_PREVIEW_OPENED, properties)
        }
    }

    fun performInsertAction() {
        val uriList = selectedURIs()
        _onInsert.value = Event(uriList)
        val isMultiselection = uriList.size > 1
        for (mediaUri in uriList) {
            val isVideo = MediaUtils.isVideo(mediaUri.toString())
            val properties = analyticsUtilsWrapper.getMediaProperties(
                    isVideo,
                    mediaUri,
                    null
            )
            properties["is_part_of_multiselection"] = isMultiselection
            if (isMultiselection) {
                properties["number_of_media_selected"] = uriList.size
            }
            analyticsTrackerWrapper.track(MEDIA_PICKER_RECENT_MEDIA_SELECTED, properties)
        }
    }

    fun clickOnLastTappedIcon() = clickIcon(lastTappedIcon!!)

    fun clickIcon(icon: PhotoPickerIcon) {
        if (icon == ANDROID_CAPTURE_PHOTO || icon == ANDROID_CAPTURE_VIDEO || icon == WP_STORIES_CAPTURE) {
            if (!permissionsHandler.hasPermissionsToAccessPhotos()) {
                _onPermissionsRequested.value = Event(PermissionsRequested.CAMERA)
                lastTappedIcon = icon
                return
            }
        }
        when (icon) {
            ANDROID_CAPTURE_PHOTO -> trackSelectedOtherSourceEvents(
                    MEDIA_PICKER_OPEN_CAPTURE_MEDIA,
                    false
            )
            ANDROID_CAPTURE_VIDEO -> trackSelectedOtherSourceEvents(
                    MEDIA_PICKER_OPEN_CAPTURE_MEDIA,
                    true
            )
            ANDROID_CHOOSE_PHOTO -> trackSelectedOtherSourceEvents(
                    MEDIA_PICKER_OPEN_DEVICE_LIBRARY,
                    false
            )
            ANDROID_CHOOSE_VIDEO -> trackSelectedOtherSourceEvents(
                    MEDIA_PICKER_OPEN_DEVICE_LIBRARY,
                    true
            )
            WP_MEDIA -> AnalyticsTracker.track(MEDIA_PICKER_OPEN_WP_MEDIA)
            STOCK_MEDIA -> {
            }
            GIF -> {
            }
            WP_STORIES_CAPTURE -> AnalyticsTracker.track(MEDIA_PICKER_OPEN_WP_STORIES_CAPTURE)
            ANDROID_CHOOSE_PHOTO_OR_VIDEO -> {
            }
        }
        _onIconClicked.postValue(Event(IconClickEvent(icon, browserType.canMultiselect())))
    }

    private fun trackSelectedOtherSourceEvents(stat: Stat, isVideo: Boolean) {
        val properties: MutableMap<String, Any?> = HashMap()
        properties["is_video"] = isVideo
        AnalyticsTracker.track(stat, properties)
    }

    fun onCameraClicked(viewWrapper: ViewWrapper) {
        if (browserType.isImagePicker && browserType.isVideoPicker) {
            showCameraPopupMenu(viewWrapper)
        } else if (browserType.isImagePicker) {
            clickIcon(ANDROID_CAPTURE_PHOTO)
        } else if (browserType.isVideoPicker) {
            clickIcon(ANDROID_CAPTURE_VIDEO)
        } else {
            AppLog.e(
                    MEDIA,
                    "This code should be unreachable. If you see this message one of " +
                            "the MediaBrowserTypes isn't setup correctly."
            )
        }
    }

    fun showCameraPopupMenu(viewWrapper: ViewWrapper) {
        val capturePhotoItem = PopupMenuItem(UiStringRes(R.string.photo_picker_capture_photo)) {
            clickIcon(
                    ANDROID_CAPTURE_PHOTO
            )
        }
        val captureVideoItem = PopupMenuItem(UiStringRes(R.string.photo_picker_capture_video)) {
            clickIcon(
                    ANDROID_CAPTURE_VIDEO
            )
        }
        _showPopupMenu.value = Event(PopupMenuUiModel(viewWrapper, listOf(capturePhotoItem, captureVideoItem)))
    }

    fun performActionOrShowPopup(viewWrapper: ViewWrapper) {
        val items = mutableListOf<PopupMenuItem>()
        if (browserType.isImagePicker) {
            items.add(PopupMenuItem(UiStringRes(R.string.photo_picker_choose_photo)) {
                clickIcon(
                        ANDROID_CHOOSE_PHOTO
                )
            })
        }
        if (browserType.isVideoPicker) {
            items.add(PopupMenuItem(UiStringRes(R.string.photo_picker_choose_video)) {
                clickIcon(
                        ANDROID_CHOOSE_VIDEO
                )
            })
        }
        if (site != null && !browserType.isGutenbergPicker) {
            items.add(PopupMenuItem(UiStringRes(R.string.photo_picker_stock_media)) {
                clickIcon(
                        STOCK_MEDIA
                )
            })
            // only show GIF picker from Tenor if this is NOT the WPStories picker
            if (tenorFeatureConfig.isEnabled() && !browserType.isWPStoriesPicker) {
                items.add(PopupMenuItem(UiStringRes(R.string.photo_picker_gif)) {
                    clickIcon(
                            GIF
                    )
                })
            }
        }
        if (items.size == 1) {
            items[0].action()
        } else {
            _showPopupMenu.value = Event(PopupMenuUiModel(viewWrapper, items))
        }
    }

    fun checkStoragePermission(isAlwaysDenied: Boolean) {
        if (permissionsHandler.hasStoragePermission()) {
            showSoftAskView(show = false, isAlwaysDenied = isAlwaysDenied)
            if (_data.value.isNullOrEmpty()) {
                refreshData(false)
            }
        } else showSoftAskView(show = true, isAlwaysDenied = isAlwaysDenied)
    }

    private fun showSoftAskView(show: Boolean, isAlwaysDenied: Boolean) {
        if (show) {
            val resources = context.resources
            val appName = "<strong>${resources.getString(R.string.app_name)}</strong>"
            val label = if (isAlwaysDenied) {
                val permissionName = ("<strong>${WPPermissionUtils.getPermissionName(
                        context,
                        permission.WRITE_EXTERNAL_STORAGE
                )}</strong>")
                String.format(
                        resources.getString(R.string.photo_picker_soft_ask_permissions_denied), appName,
                        permissionName
                )
            } else {
                String.format(
                        resources.getString(R.string.photo_picker_soft_ask_label),
                        appName
                )
            }
            val allowId = if (isAlwaysDenied) R.string.button_edit_permissions else R.string.photo_picker_soft_ask_allow
            _softAskViewModel.value = Show(label, UiStringRes(allowId), isAlwaysDenied)
        } else if (_softAskViewModel.value is Show) {
            _softAskViewModel.value = SoftAskViewUiModel.Hide
        }
    }

    data class PhotoPickerUiState(
        val photoListUiModel: PhotoListUiModel? = null,
        val bottomBarUiModel: BottomBarUiModel? = null,
        val softAskViewUiModel: SoftAskViewUiModel? = null,
        val fabUiModel: FabUiModel? = null
    )

    data class PhotoListUiModel(
        val items: List<PhotoPickerUiItem>,
        val count: Int = 0,
        val isVideoSelected: Boolean = false
    )

    data class BottomBarUiModel(
        val type: BottomBar,
        val insertEditTextBarVisible: Boolean,
        val hideMediaBottomBarInPortrait: Boolean,
        val showCameraButton: Boolean,
        val showWPMediaIcon: Boolean,
        val canShowInsertEditBottomBar: Boolean,
        val onIconPickerClicked: (ViewWrapper) -> Unit
    ) {
        enum class BottomBar {
            INSERT_EDIT, MEDIA_SOURCE, NONE
        }
    }

    sealed class SoftAskViewUiModel {
        data class Show(val label: String, val allowId: UiStringRes, val isAlwaysDenied: Boolean) : SoftAskViewUiModel()
        object Hide : SoftAskViewUiModel()
    }

    data class FabUiModel(val show: Boolean, val action: () -> Unit)

    data class ActionModeUiModel(val actionModeTitle: UiString? = null, val showConfirmAction: Boolean = false)

    data class IconClickEvent(val icon: PhotoPickerIcon, val allowMultipleSelection: Boolean)

    enum class PermissionsRequested {
        CAMERA, STORAGE
    }

    data class PopupMenuUiModel(val view: ViewWrapper, val items: List<PopupMenuItem>) {
        data class PopupMenuItem(val title: UiStringRes, val action: () -> Unit)
    }
}
