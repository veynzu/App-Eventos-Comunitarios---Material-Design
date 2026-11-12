package com.eventoscomunitarios.app

import com.eventoscomunitarios.app.core.data.InMemoryContentAnalysisRepository
import com.eventoscomunitarios.app.domain.model.TextAssessment
import com.eventoscomunitarios.app.domain.repository.ContentAnalysisRepository
import com.eventoscomunitarios.app.domain.usecase.ContentAnalysisUseCases
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ContentAnalysisPreparationTest {
    @Test fun preparationDoesNotApproveTextWhenThereIsNoProvider() = runBlocking {
        val cases = ContentAnalysisUseCases(InMemoryContentAnalysisRepository())
        val status = cases.status().getOrThrow()
        assertFalse(status.enabled)
        assertTrue(status.preparationOnly)
        assertTrue(cases.analyzeText("Invitación a la comunidad").isFailure)
    }

    @Test fun anExplicitProblemRetainsReasonsAndTheSuggestedAlternative() = runBlocking {
        val assessment = TextAssessment(false, listOf("Lenguaje ofensivo"), "Conversemos con respeto.")
        val cases = ContentAnalysisUseCases(InMemoryContentAnalysisRepository(textResult = assessment))
        assertEquals(assessment, cases.analyzeText("Texto para analizar").getOrThrow())
    }

    @Test fun invalidInputDoesNotInvokeTheRemoteProvider() = runBlocking {
        var calls = 0
        val repository = object : ContentAnalysisRepository by InMemoryContentAnalysisRepository() {
            override suspend fun analyzeText(text: String, language: String): TextAssessment {
                calls++
                error("No debe consultar un proveedor")
            }
        }
        val cases = ContentAnalysisUseCases(repository)
        assertTrue(cases.analyzeText(" ").isFailure)
        assertTrue(cases.analyzeText("hola", "xx").isFailure)
        assertEquals(0, calls)
    }

    @Test fun cancellationRemainsCancellationInsteadOfAValidationFailure() = runBlocking {
        val repository = object : ContentAnalysisRepository by InMemoryContentAnalysisRepository() {
            override suspend fun analyzeText(text: String, language: String): TextAssessment = throw CancellationException()
        }
        try {
            ContentAnalysisUseCases(repository).analyzeText("Invitación")
            fail("Debe conservar la cancelación del ViewModel")
        } catch (_: CancellationException) { }
    }
}
