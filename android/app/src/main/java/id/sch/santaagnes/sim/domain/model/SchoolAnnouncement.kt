package id.sch.santaagnes.sim.domain.model

/** Pengumuman sekolah atau kelas (F-PGM-01, F-PGM-02, F-PGM-03). */
data class Announcement(
    val id: Int,
    val title: String,
    val content: String,
    val scope: String,
    val classGroupId: Int? = null,
    val className: String? = null,
    val authorName: String,
    val date: String,
    val attachmentUrl: String? = null,
    val attachmentPublicId: String? = null
)

/** Hasil daftar pengumuman beserta penanda apakah berasal dari cache offline Room. */
data class AnnouncementFeed(
    val items: List<Announcement>,
    val isFromCache: Boolean = false
)

/** Metadata hasil unggah lampiran pengumuman ke Cloudinary. */
data class UploadedAttachment(
    val url: String,
    val publicId: String,
    val fileName: String
)
