package com.example.harry_android.ui.book

import androidx.compose.runtime.snapshots.SnapshotApplyResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.harry_android.core.dispatcher.DispatcherProvider
import com.example.harry_android.domain.model.Book
import com.example.harry_android.domain.repository.IRemoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BookViewState(
    val book: Book? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class BookViewModel @Inject constructor(private val harryRemoteRepository: IRemoteRepository,
    private val dispatcher: DispatcherProvider
) : ViewModel(){

    private val _bookViewState = MutableStateFlow(BookViewState())

    val bookViewState: StateFlow<BookViewState> = _bookViewState.asStateFlow()

    init {
        //getBookPreview()
    }

     fun getBookPreview(){
        _bookViewState.update {
            it.copy(loading = true)
        }

        viewModelScope.launch(dispatcher.io) {
            val data  = harryRemoteRepository.getBook()

            when{
                data.isSuccess -> _bookViewState.update { it.copy(loading = false, book = data.getOrNull(), error = null) }
                data.isFailure -> _bookViewState.update { it.copy(loading = false, error = data.exceptionOrNull()?.message?:"Failed to get book for Harry potter") }
            }
        }
    }
}