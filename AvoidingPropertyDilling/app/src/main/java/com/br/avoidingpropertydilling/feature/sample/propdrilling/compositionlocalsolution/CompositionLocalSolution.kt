package com.br.avoidingpropertydilling.feature.sample.propdrilling.compositionlocalsolution

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.br.avoidingpropertydilling.feature.sample.propdrilling.patterneventsolution.UserListViewModel

/*
    https://gemini.google.com/app/bf44712b98dfdb6d

    O composition Local permite passar dados impleicitamente pela arvore de composição sem
    repassá-los explicitamente
 */


@Composable
fun UserLisCompositionLocalScreen(
    modifier: Modifier = Modifier,
    viewModel: UserListViewModel = viewModel(factory = UserListViewModel.factory)
)  {

}