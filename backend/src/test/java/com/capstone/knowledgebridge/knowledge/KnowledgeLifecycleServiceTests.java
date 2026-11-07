package com.capstone.knowledgebridge.knowledge;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import com.capstone.knowledgebridge.gbrain.GbrainClient;
import com.capstone.knowledgebridge.gbrain.GbrainException;
import com.capstone.knowledgebridge.gbrain.GbrainErrorCode;
import com.capstone.knowledgebridge.video.VideoStorageService;

class KnowledgeLifecycleServiceTests {
	private final KnowledgeItemRepository items = mock(KnowledgeItemRepository.class);
	private final GbrainClient engine = mock(GbrainClient.class);
	private final KnowledgeRevisionService revisions = mock(KnowledgeRevisionService.class);
	private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
	private final VideoStorageService storage = mock(VideoStorageService.class);
	private final KnowledgeLifecycleService service = new KnowledgeLifecycleService(items, engine, revisions, jdbc,
			storage);

	private KnowledgeItem item(KnowledgeLifecycle state, KnowledgeItemStatus status) {
		KnowledgeItem item = new KnowledgeItem("Policy", "Policy", 1L, "The retained document body.");
		ReflectionTestUtils.setField(item, "id", 1L);
		ReflectionTestUtils.setField(item, "status", status);
		if (state != KnowledgeLifecycle.ACTIVE)
			item.changeLifecycle(state);
		when(items.findForLifecycle(1L)).thenReturn(Optional.of(item));
		when(items.save(any())).thenAnswer(call -> call.getArgument(0));
		return item;
	}

	@Test
	void deactivateAndActivateCoordinateTheEngineAndInvalidateAnswers() {
		KnowledgeItem item = item(KnowledgeLifecycle.ACTIVE, KnowledgeItemStatus.COMPLETED);
		assertThat(item.isAvailable()).isTrue();
		service.change(1, KnowledgeLifecycleService.Action.DEACTIVATE);
		assertThat(item.isAvailable()).isFalse();
		assertThat(item.getLifecycleState()).isEqualTo(KnowledgeLifecycle.INACTIVE);
		assertThat(item.getSoftDeletedAt()).isNull();
		verify(engine).deleteDocument("1");
		service.change(1, KnowledgeLifecycleService.Action.ACTIVATE);
		assertThat(item.isAvailable()).isTrue();
		verify(engine).upsertDocument(any());
		verify(revisions, times(2)).bump();
	}

	@Test
	void trashRemembersPreviousStateAndRestoresInactiveWithoutIndexing() {
		KnowledgeItem item = item(KnowledgeLifecycle.INACTIVE, KnowledgeItemStatus.COMPLETED);
		service.change(1, KnowledgeLifecycleService.Action.TRASH);
		assertThat(item.getTrashedFrom()).isEqualTo(KnowledgeLifecycle.INACTIVE);
		assertThat(item.getSoftDeletedAt()).isNotNull();
		service.change(1, KnowledgeLifecycleService.Action.RESTORE);
		assertThat(item.getLifecycleState()).isEqualTo(KnowledgeLifecycle.INACTIVE);
		assertThat(item.getTrashedFrom()).isNull();
		verify(engine, never()).upsertDocument(any());
		verify(engine, times(2)).deleteDocument("1");
	}

	@Test
	void failedContentCanBeRestoredButMustBeRetriedBeforeItBecomesAvailable() {
		KnowledgeItem item = item(KnowledgeLifecycle.TRASH, KnowledgeItemStatus.FAILED);
		service.change(1, KnowledgeLifecycleService.Action.RESTORE);
		assertThat(item.getLifecycleState()).isEqualTo(KnowledgeLifecycle.ACTIVE);
		assertThat(item.isAvailable()).isFalse();
		verify(engine).deleteDocument("1");
	}

	@Test
	void activeTrashRestoreReindexesAndEngineFailureDoesNotChangeState() {
		KnowledgeItem item = item(KnowledgeLifecycle.TRASH, KnowledgeItemStatus.COMPLETED);
		service.change(1, KnowledgeLifecycleService.Action.RESTORE);
		assertThat(item.getLifecycleState()).isEqualTo(KnowledgeLifecycle.ACTIVE);
		when(engine.deleteDocument("1")).thenThrow(new GbrainException(GbrainErrorCode.UNAVAILABLE, "Unavailable"));
		assertThatThrownBy(() -> service.change(1, KnowledgeLifecycleService.Action.TRASH))
				.isInstanceOf(GbrainException.class);
		assertThat(item.getLifecycleState()).isEqualTo(KnowledgeLifecycle.ACTIVE);
	}

	@Test
	void missingBusyAndInvalidTransitionsAreRejected() {
		assertThatThrownBy(() -> service.change(99, KnowledgeLifecycleService.Action.TRASH))
				.isInstanceOf(ResponseStatusException.class);
		for (KnowledgeItemStatus status : new KnowledgeItemStatus[]{KnowledgeItemStatus.PENDING,
				KnowledgeItemStatus.PROCESSING}) {
			item(KnowledgeLifecycle.ACTIVE, status);
			assertThatThrownBy(() -> service.change(1, KnowledgeLifecycleService.Action.TRASH))
					.isInstanceOf(ResponseStatusException.class);
		}
		for (KnowledgeLifecycleService.Action action : new KnowledgeLifecycleService.Action[]{
				KnowledgeLifecycleService.Action.ACTIVATE, KnowledgeLifecycleService.Action.RESTORE}) {
			item(KnowledgeLifecycle.ACTIVE, KnowledgeItemStatus.COMPLETED);
			assertThatThrownBy(() -> service.change(1, action)).isInstanceOf(ResponseStatusException.class);
		}
		item(KnowledgeLifecycle.INACTIVE, KnowledgeItemStatus.COMPLETED);
		assertThatThrownBy(() -> service.change(1, KnowledgeLifecycleService.Action.DEACTIVATE))
				.isInstanceOf(ResponseStatusException.class);
		item(KnowledgeLifecycle.TRASH, KnowledgeItemStatus.COMPLETED);
		assertThatThrownBy(() -> service.change(1, KnowledgeLifecycleService.Action.TRASH))
				.isInstanceOf(ResponseStatusException.class);
		item(KnowledgeLifecycle.ACTIVE, KnowledgeItemStatus.COMPLETED);
		assertThatThrownBy(() -> service.permanentlyDelete(1)).isInstanceOf(ResponseStatusException.class);
		verifyNoInteractions(engine);
	}

	@Test
	void permanentDeletionRemovesMetadataCacheAndOnlyTheRetainedFile() {
		KnowledgeItem item = item(KnowledgeLifecycle.TRASH, KnowledgeItemStatus.COMPLETED);
		service.permanentlyDelete(1);
		verify(storage, never()).deleteRetainedSource(any());
		ReflectionTestUtils.setField(item, "sourcePath", "retained.mp4");
		service.permanentlyDelete(1);
		verify(storage).deleteRetainedSource(Path.of("retained.mp4"));
		verify(items, times(2)).delete(item);
		verify(items, times(2)).flush();
		verify(jdbc, times(2)).update("DELETE FROM ingestion_attempt WHERE knowledge_item_id = ?", 1L);
		verify(jdbc, times(2)).update("DELETE FROM query_cache");
	}

	@Test
	void controllerMapsAllAdminActionsToNoContent() {
		KnowledgeLifecycleService mocked = mock(KnowledgeLifecycleService.class);
		KnowledgeLifecycleController controller = new KnowledgeLifecycleController(mocked);
		assertThat(controller.deactivate(1).getStatusCode().value()).isEqualTo(204);
		assertThat(controller.activate(1).getStatusCode().value()).isEqualTo(204);
		assertThat(controller.trash(1).getStatusCode().value()).isEqualTo(204);
		assertThat(controller.restore(1).getStatusCode().value()).isEqualTo(204);
		assertThat(controller.permanentlyDelete(1).getStatusCode().value()).isEqualTo(204);
		for (KnowledgeLifecycleService.Action action : KnowledgeLifecycleService.Action.values())
			verify(mocked).change(1, action);
		verify(mocked).permanentlyDelete(1);
	}
}
