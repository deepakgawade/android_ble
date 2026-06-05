package com.example.harry_android.ui.book

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.sharp.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import com.example.harry_android.domain.model.Book
import com.example.harry_android.ui.theme.Harry_androidTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookScreen(viewModel: BookViewModel = hiltViewModel()) {
    val state by viewModel.bookViewState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
viewModel.getBookPreview()
    }
    BookScreenContent(state = state, onGetBook = viewModel::getBookPreview)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookScreenContent(state: BookViewState, onGetBook: () -> Unit) {




    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Harry's Books") },
                actions = {
                    IconButton(
                        onClick = onGetBook,
                        content = {
                            Icon(
                                imageVector = Icons.Sharp.Refresh,
                                contentDescription = "Refresh",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when {
                state.loading -> Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("Loading...")
                    Spacer(Modifier.height(16.dp))
                    CircularProgressIndicator()
                }
                state.error != null -> Text("Error: ${state.error}")
                state.book != null -> BookCard(state.book)
                else -> Button(onClick = onGetBook) { Text("Get Book") }
            }
        }
    }
}

@Composable
private fun BookCard(book: Book) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(16.dp)
    ) {
        Text(book.title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        SubcomposeAsyncImage(
            model = book.coverUrl,
            contentDescription = book.title,
            modifier = Modifier.fillMaxWidth(),
            loading = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        )
        Text(book.description, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
    }
}

private val previewBook = Book(
    number = 5,
    title = "Harry Potter and the Order of the Phoenix",
    originalTitle = "Harry Potter and the Order of the Phoenix",
    releaseDate = "Jun 21, 2003",
    description = "In his fifth year at Hogwarts, Harry discovers that many members of the wizarding community do not know the truth about his encounter with Lord Voldemort. Cornelius Fudge, Minister of Magic, appoints Dolores Umbridge as Defense Against the Dark Arts teacher because he believes that Professor Dumbledore plans to take over his job. But his teachings are inadequate, so Harry prepares the students to defend the school against evil.",
    pages = 766,
    coverUrl = "https://raw.githubusercontent.com/fedeperin/potterapi/main/public/images/covers/5.png"
)

@Preview(showBackground = true, name = "Idle - Get Book button")
@Composable
private fun BookScreenIdlePreview() {
    Harry_androidTheme {
        BookScreenContent(state = BookViewState(), onGetBook = {})
    }
}

@Preview(showBackground = true, name = "Loading")
@Composable
private fun BookScreenLoadingPreview() {
    Harry_androidTheme {
        BookScreenContent(state = BookViewState(loading = true), onGetBook = {})
    }
}

@Preview(showBackground = true, name = "Error")
@Composable
private fun BookScreenErrorPreview() {
    Harry_androidTheme {
        BookScreenContent(
            state = BookViewState(error = "Network error: unable to reach server"),
            onGetBook = {}
        )
    }
}

@Preview(showBackground = true, name = "Book loaded")
@Composable
private fun BookScreenBookPreview() {
    Harry_androidTheme {
        BookScreenContent(state = BookViewState(book = previewBook), onGetBook = {})
    }
}