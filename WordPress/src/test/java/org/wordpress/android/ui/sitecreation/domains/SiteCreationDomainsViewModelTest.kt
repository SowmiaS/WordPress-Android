package org.wordpress.android.ui.sitecreation.domains

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.firstValue
import com.nhaarman.mockitokotlin2.isNull
import com.nhaarman.mockitokotlin2.lastValue
import com.nhaarman.mockitokotlin2.secondValue
import com.nhaarman.mockitokotlin2.thirdValue
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.InternalCoroutinesApi
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.wordpress.android.R
import org.wordpress.android.TEST_DISPATCHER
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.network.rest.wpcom.site.DomainSuggestionResponse
import org.wordpress.android.fluxc.store.SiteStore.OnSuggestedDomains
import org.wordpress.android.fluxc.store.SiteStore.SuggestDomainError
import org.wordpress.android.test
import org.wordpress.android.ui.sitecreation.domains.SiteCreationDomainsViewModel.DomainsListItemUiState.DomainsFetchSuggestionsErrorUiState
import org.wordpress.android.ui.sitecreation.domains.SiteCreationDomainsViewModel.DomainsListItemUiState.DomainsModelUiState.DomainsModelAvailableUiState
import org.wordpress.android.ui.sitecreation.domains.SiteCreationDomainsViewModel.DomainsListItemUiState.DomainsModelUiState.DomainsModelUnavailabilityUiState
import org.wordpress.android.ui.sitecreation.domains.SiteCreationDomainsViewModel.DomainsUiState
import org.wordpress.android.ui.sitecreation.domains.SiteCreationDomainsViewModel.DomainsUiState.DomainsUiContentState
import org.wordpress.android.ui.sitecreation.misc.SiteCreationTracker
import org.wordpress.android.ui.sitecreation.usecases.FetchDomainsUseCase
import org.wordpress.android.ui.utils.UiString.UiStringRes
import org.wordpress.android.util.NetworkUtilsWrapper

private const val MULTI_RESULT_DOMAIN_FETCH_RESULT_SIZE = 20
private val MULTI_RESULT_DOMAIN_FETCH_QUERY = "multi_result_query" to MULTI_RESULT_DOMAIN_FETCH_RESULT_SIZE
private val EMPTY_RESULT_DOMAIN_FETCH_QUERY = "empty_result_query" to 0

@InternalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class SiteCreationDomainsViewModelTest {
    @Rule
    @JvmField val rule = InstantTaskExecutorRule()

    @Mock lateinit var dispatcher: Dispatcher
    @Mock lateinit var fetchDomainsUseCase: FetchDomainsUseCase
    @Mock private lateinit var tracker: SiteCreationTracker
    @Mock private lateinit var uiStateObserver: Observer<DomainsUiState>
    @Mock private lateinit var createSiteBtnObserver: Observer<String>
    @Mock private lateinit var clearBtnObserver: Observer<Unit>
    @Mock private lateinit var onHelpClickedObserver: Observer<Unit>
    @Mock private lateinit var networkUtils: NetworkUtilsWrapper
    @Mock private lateinit var mSiteCreationDomainSanitizer: SiteCreationDomainSanitizer

    private lateinit var viewModel: SiteCreationDomainsViewModel

    @Before
    fun setUp() {
        viewModel = SiteCreationDomainsViewModel(
                networkUtils = networkUtils,
                domainSanitizer = mSiteCreationDomainSanitizer,
                dispatcher = dispatcher,
                fetchDomainsUseCase = fetchDomainsUseCase,
                tracker = tracker,
                bgDispatcher = TEST_DISPATCHER,
                mainDispatcher = TEST_DISPATCHER
        )
        viewModel.uiState.observeForever(uiStateObserver)
        viewModel.createSiteBtnClicked.observeForever(createSiteBtnObserver)
        viewModel.clearBtnClicked.observeForever(clearBtnObserver)
        viewModel.onHelpClicked.observeForever(onHelpClickedObserver)
        whenever(networkUtils.isNetworkAvailable()).thenReturn(true)
    }

    private fun <T> testWithSuccessResponse(
        queryResultSizePair: Pair<String, Int> = MULTI_RESULT_DOMAIN_FETCH_QUERY,
        isDomainAvailableInSuggestions: Boolean = true,
        block: suspend CoroutineScope.() -> T
    ) {
        test {
            whenever(mSiteCreationDomainSanitizer.getName(any())).thenReturn(
                    "${MULTI_RESULT_DOMAIN_FETCH_QUERY.first}-1"
            )
            whenever(mSiteCreationDomainSanitizer.getDomain(any())).thenReturn(
                    ".wordpress.com"
            )
            whenever(mSiteCreationDomainSanitizer.sanitizeDomainQuery(any())).thenReturn(
                    createSanitizedDomainResult(isDomainAvailableInSuggestions)
            )
            whenever(
                    fetchDomainsUseCase.fetchDomains(
                            eq(queryResultSizePair.first),
                            isNull(),
                            eq(true),
                            eq(true),
                            any()
                    )
            ).thenReturn(createSuccessfulOnSuggestedDomains(queryResultSizePair))
            block()
        }
    }

    /**
     * Verifies the UI state for when the VM is started with an empty site title.
     */
    @Test
    fun verifyEmptyTitleQueryUiState() = testWithSuccessResponse {
        viewModel.start()
        verifyInitialContentUiState(requireNotNull(viewModel.uiState.value), showProgress = false)
    }

    /**
     * Verifies the initial UI state for when the user enters a non-empty query.
     */
    @Test
    fun verifyNonEmptyUpdateQueryInitialUiState() = testWithSuccessResponse {
        viewModel.start()
        viewModel.updateQuery(MULTI_RESULT_DOMAIN_FETCH_QUERY.first)
        val captor = ArgumentCaptor.forClass(DomainsUiState::class.java)
        verify(uiStateObserver, times(3)).onChanged(captor.capture())
        verifyInitialContentUiState(captor.secondValue, showProgress = true, showClearButton = true)
    }

    /**
     * Verifies the UI state for after the user enters a non-empty query which results in no domain suggestions.
     */
    @Test
    fun verifyNonEmptyUpdateQueryUiStateAfterResponseWithEmptyResults() =
            testWithSuccessResponse(queryResultSizePair = EMPTY_RESULT_DOMAIN_FETCH_QUERY) {
                viewModel.start()
                viewModel.updateQuery(EMPTY_RESULT_DOMAIN_FETCH_QUERY.first)
                val captor = ArgumentCaptor.forClass(DomainsUiState::class.java)
                verify(uiStateObserver, times(3)).onChanged(captor.capture())
                verifyEmptyItemsContentUiState(
                        captor.thirdValue,
                        showClearButton = true
                )
            }

    /**
     * Verifies the UI state for after the user enters a non-empty query which results in multiple domain suggestions.
     */
    @Test
    fun verifyNonEmptyUpdateQueryUiStateAfterResponseWithMultipleResults() =
            testWithSuccessResponse {
                viewModel.start()
                viewModel.updateQuery(MULTI_RESULT_DOMAIN_FETCH_QUERY.first)
                val captor = ArgumentCaptor.forClass(DomainsUiState::class.java)
                verify(uiStateObserver, times(3)).onChanged(captor.capture())
                verifyVisibleItemsContentUiState(captor.thirdValue, showClearButton = true)
            }

    /**
     * Verifies the UI state for after the user enters a query that is unavailable which results in the domain
     * unavailability list item being shown in the domain suggestions.
     */
    @Test
    fun verifyDomainUnavailableUiStateAfterResponseWithMultipleResults() =
            testWithSuccessResponse(isDomainAvailableInSuggestions = false) {
                viewModel.start()
                viewModel.updateQuery(MULTI_RESULT_DOMAIN_FETCH_QUERY.first)
                val captor = ArgumentCaptor.forClass(DomainsUiState::class.java)
                verify(uiStateObserver, times(3)).onChanged(captor.capture())
                verifyContentAndDomainValidityUiStatesAreVisible(
                        captor.thirdValue
                )
            }

    /**
     * Verifies the UI state for after the user enters a non-empty query which results in error.
     */
    @Test
    fun verifyNonEmptyUpdateQueryUiStateAfterErrorResponse() = test {
        val queryResultErrorPair = "error_result_query" to "GENERIC_ERROR"
        whenever(
                fetchDomainsUseCase.fetchDomains(
                        eq(queryResultErrorPair.first),
                        isNull(),
                        eq(true),
                        eq(true),
                        any()
                )
        ).thenReturn(createFailedOnSuggestedDomains(queryResultErrorPair))

        viewModel.start()
        viewModel.updateQuery(queryResultErrorPair.first)

        val captor = ArgumentCaptor.forClass(DomainsUiState::class.java)
        verify(uiStateObserver, times(3)).onChanged(captor.capture())
        verifyVisibleItemsContentUiState(
                captor.thirdValue,
                showClearButton = true,
                numberOfItems = 1
        )
        assertThat(captor.thirdValue.contentState.items[0])
                .isInstanceOf(DomainsFetchSuggestionsErrorUiState::class.java)
    }

    /**
     * Verifies the UI state for after the user enters a non-empty query which results in INVALID_QUERY error.
     */
    @Test
    fun verifyNonEmptyUpdateQueryUiStateAfterErrorResponseOfTypeInvalidQuery() = test {
        val queryResultErrorPair = "empty_result_query_invalid" to "INVALID_QUERY"
        whenever(
                fetchDomainsUseCase.fetchDomains(
                        eq(queryResultErrorPair.first),
                        isNull(),
                        eq(true),
                        eq(true),
                        any()
                )
        ).thenReturn(createFailedOnSuggestedDomains(queryResultErrorPair))

        viewModel.start()
        viewModel.updateQuery(queryResultErrorPair.first)

        val captor = ArgumentCaptor.forClass(DomainsUiState::class.java)
        verify(uiStateObserver, times(3)).onChanged(captor.capture())
        verifyEmptyItemsContentUiState(
                captor.thirdValue,
                showClearButton = true,
                isInvalidQuery = true
        )
    }

    /**
     * Verifies the UI state after the user enters an empty query (presses clear button) with an empty site title
     * which results in initial UI state
     */
    @Test
    fun verifyClearQueryWithEmptyTitleInitialState() = testWithSuccessResponse {
        viewModel.start()
        viewModel.updateQuery(MULTI_RESULT_DOMAIN_FETCH_QUERY.first)
        viewModel.updateQuery("")
        val captor = ArgumentCaptor.forClass(DomainsUiState::class.java)
        verify(uiStateObserver, times(4)).onChanged(captor.capture())
        verifyInitialContentUiState(captor.lastValue)
    }

    /**
     * Verifies that help button is properly propagated.
     */
    @Test
    fun verifyOnHelpClickedPropagated() = testWithSuccessResponse {
        viewModel.onHelpClicked()
        val captor = ArgumentCaptor.forClass(Unit::class.java)
        verify(onHelpClickedObserver, times(1)).onChanged(captor.capture())
    }

    /**
     * Verifies that clear button is properly propagated.
     */
    @Test
    fun verifyOnClearBtnClickedPropagated() = testWithSuccessResponse {
        viewModel.onClearTextBtnClicked()
        val captor = ArgumentCaptor.forClass(Unit::class.java)
        verify(clearBtnObserver, times(1)).onChanged(captor.capture())
    }

    /**
     * Verifies that create site button is properly propagated when a domain is selected.
     */
    @Test
    fun verifyCreateSiteBtnClickedPropagated() = testWithSuccessResponse {
        val domainName = "test.domain"
        viewModel.setSelectedDomainName(domainName)
        viewModel.createSiteBtnClicked()
        val captor = ArgumentCaptor.forClass(String::class.java)
        verify(createSiteBtnObserver, times(1)).onChanged(captor.capture())
        assertThat(captor.firstValue).isEqualTo(domainName)
    }

    /**
     * Helper function to verify a [DomainsUiState] with [DomainsUiContentState.Initial] content state.
     */
    private fun verifyInitialContentUiState(
        uiState: DomainsUiState,
        showProgress: Boolean = false,
        showClearButton: Boolean = false
    ) {
        assertThat(uiState.searchInputUiState.showProgress).isEqualTo(showProgress)
        assertThat(uiState.searchInputUiState.showClearButton).isEqualTo(showClearButton)
        assertThat(uiState.contentState).isInstanceOf(DomainsUiContentState.Initial::class.java)
        assertThat(uiState.createSiteButtonContainerVisibility).isEqualTo(false)
    }

    /**
     * Helper function to verify a [DomainsUiState] with [DomainsUiContentState.VisibleItems] content state.
     */
    private fun verifyVisibleItemsContentUiState(
        uiState: DomainsUiState,
        showClearButton: Boolean = false,
        numberOfItems: Int = MULTI_RESULT_DOMAIN_FETCH_RESULT_SIZE
    ) {
        assertThat(uiState.searchInputUiState.showProgress).isEqualTo(false)
        assertThat(uiState.searchInputUiState.showClearButton).isEqualTo(showClearButton)
        assertThat(uiState.contentState).isInstanceOf(DomainsUiContentState.VisibleItems::class.java)
        assertThat(uiState.contentState.items.size).isEqualTo(numberOfItems)
    }

    /**
     * Helper function to verify a [DomainsModelUnavailabilityUiState] Ui State.
     */
    private fun verifyContentAndDomainValidityUiStatesAreVisible(
        uiState: DomainsUiState
    ) {
        assertThat(uiState.contentState.items.first())
                .isInstanceOf(DomainsModelUnavailabilityUiState::class.java)
    }

    /**
     * Helper function to verify a [DomainsUiState] with [DomainsUiContentState.Empty] content state.
     */
    private fun verifyEmptyItemsContentUiState(
        uiState: DomainsUiState,
        showClearButton: Boolean = false,
        isInvalidQuery: Boolean = false
    ) {
        assertThat(uiState.searchInputUiState.showProgress).isEqualTo(false)
        assertThat(uiState.searchInputUiState.showClearButton).isEqualTo(showClearButton)
        assertThat(uiState.contentState).isInstanceOf(DomainsUiContentState.Empty::class.java)
        val contentStateAsEmpty = uiState.contentState as DomainsUiContentState.Empty
        assertThat(contentStateAsEmpty.message).isInstanceOf(UiStringRes::class.java)
        val expectedEmptyListTextMessage = if (isInvalidQuery) {
            R.string.new_site_creation_empty_domain_list_message_invalid_query
        } else {
            R.string.new_site_creation_empty_domain_list_message
        }
        assertThat((contentStateAsEmpty.message as UiStringRes).stringRes).isEqualTo(expectedEmptyListTextMessage)
        assertThat(uiState.contentState.items.size).isEqualTo(0)
    }

    /**
     * Helper function that creates an [OnSuggestedDomains] event for the given query and number of results pair.
     */
    private fun createSuccessfulOnSuggestedDomains(queryResultSizePair: Pair<String, Int>): OnSuggestedDomains {
        val suggestions = (0 until queryResultSizePair.second).map {
            val response = DomainSuggestionResponse()
            response.domain_name = "${queryResultSizePair.first}-$it.wordpress.com"
            response
        }
        return OnSuggestedDomains(queryResultSizePair.first, suggestions)
    }

    /**
     * Helper function that creates an error [OnSuggestedDomains] event.
     */
    private fun createFailedOnSuggestedDomains(queryResultErrorPair: Pair<String, String>): OnSuggestedDomains {
        return OnSuggestedDomains(queryResultErrorPair.first, emptyList())
                .apply {
                    error = SuggestDomainError(queryResultErrorPair.second, "test")
                }
    }

    /**
     * Helper function that creates the current sanitized query being used to generate the domain suggestions.
     * It returns a test domain that's based on the test suggestions being used so that the app can behave in it's
     * normal [DomainsModelAvailableUiState] state. It also returns an unavailable domain query so that the
     *  [DomainsModelUnavailabilityUiState] state is activated.
     */
    private fun createSanitizedDomainResult(isDomainAvailableInSuggestions: Boolean) =
            if (isDomainAvailableInSuggestions) {
                "${MULTI_RESULT_DOMAIN_FETCH_QUERY.first}-1"
            } else {
                "invaliddomain"
            }
}
