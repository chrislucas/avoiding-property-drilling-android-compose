package com.br.avoidingpropertydilling.feature.sample.propdrilling.patterneventsolution

import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests da [UserListViewModel] rodando na JVM.
 *
 * - kotlinx-coroutines-test controla o viewModelScope (Dispatchers.Main) via
 *   um StandardTestDispatcher instalado com Dispatchers.setMain.
 * - MockK fornece as respostas do [UserRepository] (coEvery).
 * - SavedStateHandle usa uma instancia real (puro JVM), permitindo verificar
 *   a persistencia do filtro.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UserListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository: UserRepository = mockk()

    private val users = listOf(
        User(id = "1", name = "John Doe"),
        User(id = "2", name = "Jane Smith"),
        User(id = "3", name = "Alice Johnson"),
    )

    private fun buildViewModel(
        savedStateHandle: SavedStateHandle = SavedStateHandle()
    ) = UserListViewModel(repository, savedStateHandle)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `estado inicial e Idle`() = runTest {
        val viewModel = buildViewModel()

        assertEquals(UserListUIState.Idle, viewModel.userListUIState.value)
    }

    @Test
    fun `getUsers com sucesso emite OnSuccess com os usuarios do repositorio`() = runTest {
        coEvery { repository.getUsers() } returns RequestResult.Success(users)
        val viewModel = buildViewModel()

        viewModel.getUsers()
        advanceUntilIdle()

        val state = viewModel.userListUIState.value
        assertTrue(state is UserListUIState.OnSuccess)
        assertEquals(users, (state as UserListUIState.OnSuccess).data.users)
        coVerify(exactly = 1) { repository.getUsers() }
    }

    @Test
    fun `Refresh dispara nova busca no repositorio`() = runTest {
        coEvery { repository.getUsers() } returns RequestResult.Success(users)
        val viewModel = buildViewModel()

        viewModel.getUsers()
        // advanceUntilIdle ANTES do onEvent: getUsers() agenda uma corrotina em
        // viewModelScope.launch (sobre o StandardTestDispatcher). O StandardTestDispatcher
        // NAO executa a corrotina imediatamente - ela so roda quando o scheduler avanca.
        // advanceUntilIdle executa todas as corrotinas pendentes ate o scheduler ficar
        // ocioso, garantindo que a 1a carga realmente completou antes de prosseguir.
        advanceUntilIdle()
        viewModel.onEvent(UserAction.Refresh)
        // advanceUntilIdle DEPOIS do onEvent: Refresh chama getUsers() de novo, agendando
        // outra corrotina que tambem fica pendente. Sem este segundo advanceUntilIdle, a
        // 2a chamada ainda nao teria rodado e o coVerify abaixo falharia (veria 1 chamada).
        advanceUntilIdle()

        // getUsers inicial + Refresh = 2 chamadas ao repositorio.
        coVerify(exactly = 2) { repository.getUsers() }
    }

    @Test
    fun `Refresh atualiza a lista com os novos dados do repositorio`() = runTest {
        val primeiraCarga = listOf(User(id = "1", name = "John Doe"))
        val segundaCarga = listOf(
            User(id = "1", name = "John Doe"),
            User(id = "2", name = "Jane Smith"),
        )
        // returnsMany define respostas DIFERENTES para chamadas consecutivas do mesmo
        // metodo: a 1a chamada de getUsers() retorna primeiraCarga e a 2a retorna
        // segundaCarga. Isso e necessario para simular que o Refresh busca dados novos -
        // com um unico returns, ambas as chamadas devolveriam a mesma lista e o teste
        // nao conseguiria distinguir a carga inicial do refresh.
        coEvery { repository.getUsers() } returnsMany listOf(
            RequestResult.Success(primeiraCarga),
            RequestResult.Success(segundaCarga),
        )
        val viewModel = buildViewModel()

        viewModel.getUsers()
        advanceUntilIdle()
        viewModel.onEvent(UserAction.Refresh)
        advanceUntilIdle()

        val state = viewModel.userListUIState.value as UserListUIState.OnSuccess
        assertEquals(segundaCarga, state.data.users)
    }

    @Test
    fun `Refresh que falha emite Error`() = runTest {
        // returnsMany tambem permite misturar sucesso e falha entre as chamadas:
        // a carga inicial e um Success e o refresh seguinte e um Failure, verificando
        // que uma falha no Refresh leva o estado a Error.
        coEvery { repository.getUsers() } returnsMany listOf(
            RequestResult.Success(users),
            RequestResult.Failure(RuntimeException("Falha no refresh")),
        )
        val viewModel = buildViewModel()

        viewModel.getUsers()
        advanceUntilIdle()
        viewModel.onEvent(UserAction.Refresh)
        advanceUntilIdle()

        val state = viewModel.userListUIState.value as UserListUIState.Error
        assertEquals("Falha no refresh", state.message)
    }

    @Test
    fun `getUsers com falha emite Error com a mensagem da excecao`() = runTest {
        coEvery { repository.getUsers() } returns
            RequestResult.Failure(RuntimeException("Falha de rede"))
        val viewModel = buildViewModel()

        viewModel.getUsers()
        advanceUntilIdle()

        val state = viewModel.userListUIState.value
        assertTrue(state is UserListUIState.Error)
        assertEquals("Falha de rede", (state as UserListUIState.Error).message)
    }

    @Test
    fun `getUsers com falha sem mensagem usa texto padrao`() = runTest {
        coEvery { repository.getUsers() } returns RequestResult.Failure(RuntimeException())
        val viewModel = buildViewModel()

        viewModel.getUsers()
        advanceUntilIdle()

        val state = viewModel.userListUIState.value as UserListUIState.Error
        assertEquals("Erro ao carregar usuarios", state.message)
    }

    @Test
    fun `addUser adiciona na copia de trabalho sem alterar a fonte da verdade`() = runTest {
        coEvery { repository.getUsers() } returns RequestResult.Success(users)
        val viewModel = buildViewModel()
        viewModel.getUsers()
        advanceUntilIdle()

        val novo = User(id = "99", name = "New Person")
        viewModel.onEvent(UserAction.AddUserOnClick(novo))

        val state = viewModel.userListUIState.value as UserListUIState.OnSuccess
        assertEquals(users.size + 1, state.data.users.size)
        assertTrue(state.data.users.contains(novo))
    }

    @Test
    fun `deleteUser remove apenas o usuario indicado`() = runTest {
        coEvery { repository.getUsers() } returns RequestResult.Success(users)
        val viewModel = buildViewModel()
        viewModel.getUsers()
        advanceUntilIdle()

        viewModel.onEvent(UserAction.DeleteUserOnClick("2"))

        val state = viewModel.userListUIState.value as UserListUIState.OnSuccess
        assertEquals(listOf("1", "3"), state.data.users.map { it.id })
    }

    @Test
    fun `filterByName filtra ignorando maiusculas e minusculas`() = runTest {
        coEvery { repository.getUsers() } returns RequestResult.Success(users)
        val viewModel = buildViewModel()
        viewModel.getUsers()
        advanceUntilIdle()

        viewModel.onEvent(UserAction.FilterByName("jane"))

        val state = viewModel.userListUIState.value as UserListUIState.OnSuccess
        assertEquals(listOf("Jane Smith"), state.data.users.map { it.name })
        assertEquals("jane", state.data.filter)
    }

    @Test
    fun `filtro vazio mostra todos os usuarios de trabalho`() = runTest {
        coEvery { repository.getUsers() } returns RequestResult.Success(users)
        val viewModel = buildViewModel()
        viewModel.getUsers()
        advanceUntilIdle()

        viewModel.onEvent(UserAction.FilterByName("Jane"))
        viewModel.onEvent(UserAction.FilterByName(""))

        val state = viewModel.userListUIState.value as UserListUIState.OnSuccess
        assertEquals(users.size, state.data.users.size)
    }

    @Test
    fun `reset restaura a lista original e limpa o filtro`() = runTest {
        coEvery { repository.getUsers() } returns RequestResult.Success(users)
        val viewModel = buildViewModel()
        viewModel.getUsers()
        advanceUntilIdle()

        // Modifica o estado: adiciona, remove e filtra.
        viewModel.onEvent(UserAction.AddUserOnClick(User(id = "99", name = "Temp")))
        viewModel.onEvent(UserAction.DeleteUserOnClick("1"))
        viewModel.onEvent(UserAction.FilterByName("Jane"))

        viewModel.onEvent(UserAction.Reset)

        val state = viewModel.userListUIState.value as UserListUIState.OnSuccess
        assertEquals(users, state.data.users)
        assertEquals("", state.data.filter)
    }

    @Test
    fun `filtro e persistido no SavedStateHandle`() = runTest {
        coEvery { repository.getUsers() } returns RequestResult.Success(users)
        val handle = SavedStateHandle()
        val viewModel = buildViewModel(handle)
        viewModel.getUsers()
        advanceUntilIdle()

        viewModel.onEvent(UserAction.FilterByName("Alice"))

        assertEquals("Alice", handle.get<String>("user_list_filter"))
    }

    @Test
    fun `filtro restaurado do SavedStateHandle e aplicado ao carregar usuarios`() = runTest {
        coEvery { repository.getUsers() } returns RequestResult.Success(users)
        // Simula recriacao da ViewModel apos morte de processo com filtro salvo.
        // "doe" casa apenas com "John Doe" (evita colisao com "Johnson").
        val handle = SavedStateHandle(mapOf("user_list_filter" to "doe"))
        val viewModel = buildViewModel(handle)

        viewModel.getUsers()
        advanceUntilIdle()

        val state = viewModel.userListUIState.value as UserListUIState.OnSuccess
        assertEquals(listOf("John Doe"), state.data.users.map { it.name })
        assertEquals("doe", state.data.filter)
    }
}
