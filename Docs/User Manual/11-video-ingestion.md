# 11. Add prerecorded videos

KnowledgeBridge can transcribe prerecorded video and index the resulting text. Use this for approved meeting recordings or walkthroughs whose spoken content belongs in the shared workspace. It does not provide live meeting capture, screen understanding, a video library player, or timestamp-based citation playback.

## Prepare the recording

Supported extensions are **MP4**, **MOV**, and **WebM**. The container must be readable and contain usable audio. Silent demonstrations, on-screen text, charts, and actions that are not described aloud may not appear in the transcript or answers. Use clear audio and review important names, numbers, and decisions in the resulting text.

The default server limit is **500 MB per video** and **two hours duration**. These are configurable deployment limits, not a guarantee that every video below them can be processed. An administrator can confirm the active settings. The backend also needs working FFmpeg/FFprobe tooling and transcription-provider configuration.

## Submit a video

1. Open **Uploads**, select **File upload**, and choose the video.
2. Review the automatic title and edit it as needed.
3. Optionally enable **Use AI to name this file**. Naming uses the transcript when it becomes available.
4. Select **Add to knowledge** and wait for the upload acknowledgement.
5. Follow the item in **Knowledge list**. A queued acknowledgement means background processing still has work to do.

Folder upload can include videos as well. A batch's queued count does not mean video transcription finished.

## Follow the stages

The background pipeline can show stages such as queued work, audio extraction, transcription, optional title generation, indexing, and completion. The exact stage label helps an administrator identify where a failure occurred. Long recordings may require multiple transcription segments; completion time depends on recording size, duration, service response, and queue load.

The Uploads list polls while pending or processing work exists. Refresh if needed. Do not upload another copy solely because a recording is still in progress. A browser reload does not cancel a server-accepted background job.

## Read and verify the transcript

Once the item is active and completed, open it from Knowledge Base or a query citation. The document preview shows indexed transcript content, not the original recording. Compare uncertain details with the retained original recording outside the application. The interface does not edit video transcripts or replace an uploaded video's content in place.

The resulting text may omit audio the provider could not understand or misrecognize speakers and specialist terms. An administrator can add a separate corrective note or manage the incorrect item; clearly identify what the correction supersedes.

## Retry a failed recording

Read the item error first. After the administrator corrects a service or configuration problem, use **Retry** on the active failed item in Uploads. If transcription already succeeded and naming or indexing later failed, the retained transcript can be reused instead of transcribing again. A failure earlier in the pipeline can require the retained source to remain available.

After a transcript is durably saved, the normal pipeline removes the retained source video before optional naming/indexing completes. Failures before that point can retain source material for retry; later failures reuse the transcript. Do not assume KnowledgeBridge is a backup of the original video. Keep approved originals in your organization's own storage and follow its retention rules.

Transcription, optional AI naming, and subsequent synthesis can each incur provider charges. Query analytics do not include every video-processing cost. Canceling a query in Ask AI does not cancel an ingestion job.
