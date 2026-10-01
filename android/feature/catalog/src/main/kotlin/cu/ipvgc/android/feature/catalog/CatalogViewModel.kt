package cu.ipvgc.android.feature.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cu.ipvgc.android.core.common.Outcome
import cu.ipvgc.android.core.data.CatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CatalogUi(val rows: List<String> = emptyList(), val error: String? = null)

@HiltViewModel
class CatalogViewModel @Inject constructor(private val repo: CatalogRepository) : ViewModel() {
    private val _state = MutableStateFlow(CatalogUi())
    val state: StateFlow<CatalogUi> = _state

    init {
        viewModelScope.launch {
            val cached = repo.products(forceNetwork = false)
            if (cached is Outcome.Ok) _state.value = CatalogUi(cached.value.map { "${it.code} — ${it.name}" })
            when (val live = repo.products(forceNetwork = true)) {
                is Outcome.Ok -> _state.value = CatalogUi(live.value.map { "${it.code} — ${it.name}" })
                is Outcome.Err -> _state.value = _state.value.copy(error = live.message)
            }
        }
    }
}
