package com.br.avoidingpropertydilling.feature.sample.propdrilling.patterneventsolution

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests do [UserRepositoryMock], que agora delega a busca ao [UserClient]
 * e encapsula o retorno/erro em [RequestResult] via safeRequest.
 *
 * O [UserClient] e mockado com MockK para controlar sucesso e excecao.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UserRepositoryMockTest {

    private val client: UserClient = mockk()
    private val repository = UserRepositoryMock(client)

    private val users = listOf(
        User(id = "1", name = "John Doe"),
        User(id = "2", name = "Jane Smith"),
    )

    @Test
    fun `getUsers delega ao client e encapsula o retorno em Success`() = runTest {
        coEvery { client.getUsers() } returns users

        val result = repository.getUsers()

        assertTrue(result is RequestResult.Success)
        assertEquals(users, (result as RequestResult.Success).data)
        coVerify(exactly = 1) { client.getUsers() }
    }

    @Test
    fun `getUsers converte excecao do client em Failure`() = runTest {
        val erro = RuntimeException("Falha de rede")
        coEvery { client.getUsers() } throws erro

        val result = repository.getUsers()

        assertTrue(result is RequestResult.Failure)
        assertEquals(erro, (result as RequestResult.Failure).exception)
    }
}
