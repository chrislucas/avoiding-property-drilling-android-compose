package com.br.avoidingpropertydilling

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.br.avoidingpropertydilling.ui.theme.AvoidingPropertyDillingTheme

/*
    Como projetar uma funcao composable que chama outras compables
    de tal forma que nao precisamos passar funcoes lambdas atraves de varias delas ate chegar
    na que vai executar a acao de fato ?

    https://gemini.google.com/app/bf44712b98dfdb6d

    PropDrilling.
        - Passar funcoes lambdas para outras funcoes numa estrutura hierarquica gera um problema
        de entendimento do código, adiciona uma complexidade e complicacoes durante a escrita do
        teste

        - O nome dado a isso é prop drilling (drilling de perfuracao, como se estivessemos
        perfurando a estrutura/hierarquia e passando a funcao lambda através dela). Passar
        funcoes lambdas por varias camadas de UI polui a assionatura das funcoes

    Existem 3 padores arquiteturais principais para resolver isso no Compose, variando
    do mais idiomatico em UI ao mais focado em arquitetura de dados.

    1. Slot API (Composição em vez de Herança/Delegação)
        - A maneira mais idiomatica de evitar repasse de lambdas no COmpose é nao passar
        os dados e e as ações para baixo, mas sim passar a propria UI como um bloco (Slot).
        O componente pai lida com a ação e injeta o componente filho já configurado.

    2. Padrao de Eventos (Sealed Interface / MVI)
    3. CompositionLocal (Variáveis de Escopo)
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AvoidingPropertyDillingTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}


object PropDrillingProblems {

    @Composable
    fun Screen(onSaveClick: () -> Unit) {
        ScreenContent(action = onSaveClick)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ScreenContent(action: () -> Unit) {
        Scaffold(
            topBar = {
                TopAppBar(title = { Text("Header") })
            }
        ) { paddingValues ->
            ContentBox(modifier = Modifier.padding(paddingValues), onSaveClick = action)
        }
    }

    @Composable
    fun ContentBox(modifier: Modifier, onSaveClick: () -> Unit) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Content",
                modifier = Modifier.clickable(onClick = onSaveClick)
            )
        }
    }
}

object PropDrillingSlotApiSolution {

    /*
        Qual a diferenca aqui e por que passar uma funcao composable lambda é uma melhor solucao ?

        Note que a função onSaveClickComposable permite passar uma funcao composable e isso
        faz toda a diferença.

        No objeto PropDrillingProblems, a função lambda onSaveClick é passada pela estrutura
        hierarquica de UI até ser executada por um componente UI de uma funcao composable, ao
        passo que a função composable ScreenContent do objeto PropDrillingSolution aceita
        uma lambda composable e eu posso passar o conteúdo com o seu comportamento evitando
        POLUIR A ASSINATURA ASSINATURA DE MÉTODOS E DIMINUINDO A HIERARQUIA NA UI.
     */
    @Composable
    fun Screen(onSaveClick: () -> Unit) {
        ScreenContent { modifier ->
            // composable lambda onSaveClickComposable
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Content",
                    modifier = Modifier.clickable(onClick = onSaveClick)
                )
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ScreenContent(onSaveClickComposable: @Composable (Modifier) -> Unit) {
        Scaffold(
            topBar = {
                TopAppBar(title = { Text("Header") })
            }
        ) { paddingValues ->
            onSaveClickComposable(Modifier.padding(paddingValues))
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    AvoidingPropertyDillingTheme {
        Greeting("Android")
    }
}