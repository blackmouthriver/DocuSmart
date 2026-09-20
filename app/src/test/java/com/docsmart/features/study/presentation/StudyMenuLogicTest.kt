package com.docsmart.features.study.presentation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StudyMenuLogicTest {
    @Test
    fun `cada indice de pestana abre su vista y el menu es -1`() {
        assertEquals(StudyView.MENU, studyViewForTab(STUDY_TAB_MENU))
        assertEquals(StudyView.READING, studyViewForTab(STUDY_TAB_READING))
        assertEquals(StudyView.NOTES, studyViewForTab(STUDY_TAB_NOTES))
        assertEquals(StudyView.POMODORO, studyViewForTab(STUDY_TAB_POMODORO))
    }

    @Test
    fun `un indice fuera de rango cae al menu`() {
        assertEquals(StudyView.MENU, studyViewForTab(3))
        assertEquals(StudyView.MENU, studyViewForTab(99))
        assertEquals(StudyView.MENU, studyViewForTab(-5))
    }

    @Test
    fun `una nota concreta fuerza abrir Notas`() {
        assertEquals(STUDY_TAB_NOTES, initialStudyTab(openNoteId = "n1", requestedTab = STUDY_TAB_READING))
        assertEquals(STUDY_TAB_NOTES, initialStudyTab(openNoteId = "n1", requestedTab = STUDY_TAB_MENU))
    }

    @Test
    fun `los atajos de Inicio abren directo la vista pedida`() {
        assertEquals(STUDY_TAB_READING, initialStudyTab(null, 0))
        assertEquals(STUDY_TAB_NOTES, initialStudyTab(null, 1))
        assertEquals(STUDY_TAB_POMODORO, initialStudyTab(null, 2))
    }

    @Test
    fun `sin pedido valido abre el menu y nunca una pestana inexistente`() {
        assertEquals(STUDY_TAB_MENU, initialStudyTab(null, -1))
        assertEquals(STUDY_TAB_MENU, initialStudyTab(null, -7))
        assertEquals(STUDY_TAB_POMODORO, initialStudyTab(null, 3))
    }
}
