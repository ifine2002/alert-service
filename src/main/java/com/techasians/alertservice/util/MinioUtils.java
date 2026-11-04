package com.techasians.alertservice.util;

import com.techasians.alertservice.config.MinioProperties;
import com.techasians.alertservice.exception.ExternalApiException;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * MinioUtils - Lớp tiện ích để làm việc với MinIO Object Storage.
 *
 * Đây là lớp "helper" (trợ giúp), không phải Service hay Repository.
 * Tất cả nghiệp vụ liên quan đến lưu trữ file đều gọi qua lớp này.
 *
 * Cách dùng trong Service:
 *   @Autowired MinioUtils minioUtils;
 *   minioUtils.upload("reports/2024/file.pdf", inputStream, size, "application/pdf");
 *
 * Các chức năng chính:
 *   - Kiểm tra / tạo bucket
 *   - Upload / Download file
 *   - Xóa file (đơn lẻ và hàng loạt)
 *   - Liệt kê file theo prefix
 *   - Kiểm tra file tồn tại, lấy metadata
 *   - Tạo presigned URL (GET để tải xuống, PUT để tải lên)
 *   - Copy file
 */
@Component
public class MinioUtils {

    private static final Logger log = LoggerFactory.getLogger(MinioUtils.class);

    // Bean MinioClient được tạo trong MinioConfig, Spring tự inject vào đây
    private final MinioClient minioClient;

    // Cấu hình MinIO đọc từ application.yml (endpoint, bucket, expiry, v.v.)
    private final MinioProperties properties;

    // ========== CONSTRUCTOR ==========
    // Spring dùng constructor này để inject các dependency (MinioClient, MinioProperties)
    public MinioUtils(MinioClient minioClient, MinioProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    // ===================================================
    // PHẦN 1: QUẢN LÝ BUCKET
    // ===================================================

    /**
     * Kiểm tra bucket có tồn tại không.
     *
     * Ví dụ: bucketExists("report") → true nếu bucket "report" đã tồn tại
     */
    public boolean bucketExists(String bucket) {
        try {
            return minioClient.bucketExists(
                    BucketExistsArgs.builder()
                            .bucket(bucket)
                            .build()
            );
        } catch (Exception e) {
            throw wrap(bucket, e);
        }
    }

    /**
     * Tạo bucket nếu chưa tồn tại.
     * Nếu đã tồn tại thì bỏ qua (không báo lỗi).
     *
     * Ví dụ sai (tạo bucket mà không kiểm tra trước):
     *   minioClient.makeBucket(...); // Lỗi nếu bucket đã tồn tại
     *
     * Ví dụ đúng:
     *   minioUtils.createBucketIfNotExists("report");
     */
    public void createBucketIfNotExists(String bucket) {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder()
                            .bucket(bucket)
                            .build()
            );

            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder()
                                .bucket(bucket)
                                .build()
                );
                log.info("[MINIO] Bucket '{}' đã được tạo", bucket);
            }
        } catch (Exception e) {
            throw wrap(bucket, e);
        }
    }

    // ===================================================
    // PHẦN 2: UPLOAD FILE
    // ===================================================

    /**
     * Upload file vào bucket mặc định (cấu hình trong vcx.minio.bucket).
     *
     * @param objectName  Đường dẫn file trong MinIO, ví dụ: "reports/2024/file.pdf"
     * @param stream      Nội dung file dưới dạng InputStream
     * @param size        Kích thước file tính bằng byte
     * @param contentType Loại MIME, ví dụ: "application/pdf", "image/png"
     * @return objectName (trả về để tiện lưu vào DB)
     */
    public String upload(
            String objectName,
            InputStream stream,
            long size,
            String contentType) {

        return upload(defaultBucket(), objectName, stream, size, contentType);
    }

    /**
     * Upload file vào bucket cụ thể.
     *
     * @param bucket      Tên bucket đích
     * @param objectName  Đường dẫn file trong MinIO
     * @param stream      Nội dung file
     * @param size        Kích thước byte
     * @param contentType MIME type
     * @return objectName
     */
    public String upload(
            String bucket,
            String objectName,
            InputStream stream,
            long size,
            String contentType) {

        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .stream(stream, size, -1)
                            .contentType(contentType)
                            .build()
            );

            log.info("[MINIO UPLOAD] bucket={} object={} size={}", bucket, objectName, size);

            return objectName;

        } catch (Exception e) {
            throw wrap(bucket + "/" + objectName, e);
        }
    }

    // ===================================================
    // PHẦN 3: DOWNLOAD FILE
    // ===================================================

    /**
     * Tải xuống file từ bucket mặc định.
     *
     * @param objectName Đường dẫn file trong MinIO
     * @return InputStream để đọc nội dung file
     */
    public InputStream download(String objectName) {
        return download(defaultBucket(), objectName);
    }

    /**
     * Tải xuống file từ bucket cụ thể.
     *
     * Lưu ý: Người gọi phải tự đóng InputStream sau khi dùng xong.
     * Ví dụ: try (InputStream is = minioUtils.download("file.pdf")) { ... }
     */
    public InputStream download(String bucket, String objectName) {
        try {
            InputStream stream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .build()
            );

            log.info("[MINIO DOWNLOAD] bucket={} object={}", bucket, objectName);

            return stream;

        } catch (Exception e) {
            throw wrap(bucket + "/" + objectName, e);
        }
    }

    // ===================================================
    // PHẦN 4: XÓA FILE
    // ===================================================

    /**
     * Xóa 1 file từ bucket mặc định.
     */
    public void delete(String objectName) {
        delete(defaultBucket(), objectName);
    }

    /**
     * Xóa 1 file từ bucket cụ thể.
     */
    public void delete(String bucket, String objectName) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .build()
            );

            log.info("[MINIO DELETE] bucket={} object={}", bucket, objectName);

        } catch (Exception e) {
            throw wrap(bucket + "/" + objectName, e);
        }
    }

    /**
     * Xóa nhiều file cùng lúc từ bucket mặc định.
     *
     * @param objectNames Danh sách đường dẫn file cần xóa
     */
    public void deleteBatch(List<String> objectNames) {
        deleteBatch(defaultBucket(), objectNames);
    }

    /**
     * Xóa nhiều file cùng lúc từ bucket cụ thể.
     *
     * MinIO xóa bất đồng bộ và trả về danh sách lỗi (nếu có).
     * Chúng ta lặp qua kết quả để log ra các file xóa thất bại.
     */
    public void deleteBatch(String bucket, List<String> objectNames) {
        try {
            // Chuyển List<String> thành List<DeleteObject> mà MinIO yêu cầu
            List<DeleteObject> objects = new ArrayList<>();
            for (String name : objectNames) {
                objects.add(new DeleteObject(name));
            }

            // MinIO xóa nhiều file và trả về Iterable<Result<DeleteError>>
            Iterable<Result<DeleteError>> results = minioClient.removeObjects(
                    RemoveObjectsArgs.builder()
                            .bucket(bucket)
                            .objects(objects)
                            .build()
            );

            // Duyệt qua kết quả để phát hiện lỗi (nếu không duyệt thì MinIO không thực sự xóa)
            for (Result<DeleteError> result : results) {
                DeleteError error = result.get();
                log.warn(
                        "[MINIO DELETE ERR] bucket={} object={} message={}",
                        bucket,
                        error.objectName(),
                        error.message()
                );
            }

            log.info("[MINIO DELETE BATCH] bucket={} count={}", bucket, objectNames.size());

        } catch (Exception e) {
            throw wrap(bucket, e);
        }
    }

    // ===================================================
    // PHẦN 5: LIỆT KÊ FILE
    // ===================================================

    /**
     * Liệt kê tất cả file trong bucket mặc định có prefix cho trước.
     *
     * Ví dụ: listObjects("reports/2024/") → ["reports/2024/jan.pdf", "reports/2024/feb.pdf"]
     *
     * @param prefix Tiền tố đường dẫn để lọc, ví dụ "reports/2024/"
     * @return Danh sách objectName
     */
    public List<String> listObjects(String prefix) {
        return listObjects(defaultBucket(), prefix);
    }

    /**
     * Liệt kê tất cả file trong bucket cụ thể có prefix cho trước.
     * recursive=true: tìm cả trong thư mục con
     */
    public List<String> listObjects(String bucket, String prefix) {
        try {
            List<String> names = new ArrayList<>();

            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucket)
                            .prefix(prefix)
                            .recursive(true)
                            .build()
            );

            for (Result<Item> result : results) {
                names.add(result.get().objectName());
            }

            return names;

        } catch (Exception e) {
            throw wrap(bucket, e);
        }
    }

    // ===================================================
    // PHẦN 6: KIỂM TRA VÀ LẤY METADATA
    // ===================================================

    /**
     * Kiểm tra file có tồn tại trong bucket mặc định không.
     *
     * Cách hoạt động: gọi statObject, nếu MinIO trả về NoSuchKey thì file không tồn tại.
     *
     * @return true nếu tồn tại, false nếu không tồn tại
     */
    public boolean exists(String objectName) {
        return exists(defaultBucket(), objectName);
    }

    /**
     * Kiểm tra file có tồn tại trong bucket cụ thể không.
     */
    public boolean exists(String bucket, String objectName) {
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .build()
            );
            return true;

        } catch (ErrorResponseException e) {
            // "NoSuchKey" là mã lỗi MinIO trả về khi file không tồn tại → return false
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                return false;
            }
            // Lỗi khác (quyền truy cập, bucket không tồn tại, v.v.) → ném ra ngoài
            throw wrap(bucket + "/" + objectName, e);

        } catch (Exception e) {
            throw wrap(bucket + "/" + objectName, e);
        }
    }

    /**
     * Lấy thông tin metadata của file trong bucket mặc định.
     * Bao gồm: kích thước, contentType, last modified, v.v.
     */
    public StatObjectResponse stat(String objectName) {
        return stat(defaultBucket(), objectName);
    }

    /**
     * Lấy thông tin metadata của file trong bucket cụ thể.
     *
     * Ví dụ dùng:
     *   StatObjectResponse info = minioUtils.stat("reports/file.pdf");
     *   long fileSize = info.size();
     *   String type   = info.contentType();
     */
    public StatObjectResponse stat(String bucket, String objectName) {
        try {
            return minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .build()
            );
        } catch (Exception e) {
            throw wrap(bucket + "/" + objectName, e);
        }
    }

    // ===================================================
    // PHẦN 7: PRESIGNED URL (Đường dẫn tạm thời)
    // ===================================================

    /**
     * Tạo URL tạm thời để CLIENT tải xuống file trực tiếp từ MinIO.
     * Thời gian hết hạn = vcx.minio.presigned-get-expiry (mặc định 10 phút)
     *
     * Ví dụ dùng: trả URL cho frontend để hiển thị/tải file PDF báo cáo
     */
    public String getPresignedUrl(String objectName) {
        return getPresignedUrl(defaultBucket(), objectName, properties.getPresignedGetExpiry());
    }

    /**
     * Tạo URL tạm thời tải xuống với thời gian hết hạn tùy chỉnh.
     */
    public String getPresignedUrl(String objectName, Duration expiry) {
        return getPresignedUrl(defaultBucket(), objectName, expiry);
    }

    /**
     * Tạo URL tạm thời tải xuống từ bucket cụ thể với thời gian hết hạn tùy chỉnh.
     */
    public String getPresignedUrl(String bucket, String objectName, Duration expiry) {
        return presignedUrl(bucket, objectName, expiry, Method.GET);
    }

    /**
     * Tạo URL tạm thời để CLIENT tải lên file trực tiếp vào MinIO.
     * Thời gian hết hạn = vcx.minio.presigned-put-expiry (mặc định 15 phút)
     *
     * Ví dụ dùng: cho phép frontend upload ảnh mà không cần qua backend
     */
    public String getUploadPresignedUrl(String objectName) {
        return getUploadPresignedUrl(defaultBucket(), objectName, properties.getPresignedPutExpiry());
    }

    /**
     * Tạo URL tạm thời tải lên với thời gian hết hạn tùy chỉnh.
     */
    public String getUploadPresignedUrl(String objectName, Duration expiry) {
        return getUploadPresignedUrl(defaultBucket(), objectName, expiry);
    }

    /**
     * Tạo URL tạm thời tải lên từ bucket cụ thể với thời gian hết hạn tùy chỉnh.
     */
    public String getUploadPresignedUrl(String bucket, String objectName, Duration expiry) {
        return presignedUrl(bucket, objectName, expiry, Method.PUT);
    }

    /**
     * Hàm nội bộ tạo presigned URL cho cả GET và PUT.
     *
     * expiry.getSeconds(): chuyển Duration sang số giây nguyên (yêu cầu của MinIO SDK)
     */
    private String presignedUrl(
            String bucket,
            String objectName,
            Duration expiry,
            Method method) {

        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(method)
                            .bucket(bucket)
                            .object(objectName)
                            .expiry((int) expiry.getSeconds())
                            .build()
            );
        } catch (Exception e) {
            throw wrap(bucket + "/" + objectName, e);
        }
    }

    // ===================================================
    // PHẦN 8: SAO CHÉP FILE
    // ===================================================

    /**
     * Sao chép file trong cùng bucket mặc định.
     *
     * Ví dụ: copy("reports/draft.pdf", "reports/final.pdf")
     */
    public void copy(String sourceObject, String destObject) {
        copy(defaultBucket(), sourceObject, defaultBucket(), destObject);
    }

    /**
     * Sao chép file từ bucket nguồn sang bucket đích.
     *
     * Ví dụ dùng: sao chép file từ bucket "staging" sang "production"
     *
     * @param sourceBucket  Bucket chứa file nguồn
     * @param sourceObject  Đường dẫn file nguồn
     * @param destBucket    Bucket đích
     * @param destObject    Đường dẫn file đích
     */
    public void copy(
            String sourceBucket,
            String sourceObject,
            String destBucket,
            String destObject) {

        try {
            minioClient.copyObject(
                    CopyObjectArgs.builder()
                            .bucket(destBucket)
                            .object(destObject)
                            .source(
                                    CopySource.builder()
                                            .bucket(sourceBucket)
                                            .object(sourceObject)
                                            .build()
                            )
                            .build()
            );

            log.info(
                    "[MINIO COPY] {}/{} -> {}/{}",
                    sourceBucket, sourceObject,
                    destBucket, destObject
            );

        } catch (Exception e) {
            throw wrap(sourceBucket + "/" + sourceObject, e);
        }
    }

    // ===================================================
    // PHẦN 9: HÀM NỘI BỘ (PRIVATE)
    // ===================================================

    /**
     * Lấy bucket mặc định từ cấu hình.
     *
     * Ném IllegalStateException nếu chưa cấu hình "vcx.minio.bucket".
     * Điều này giúp phát hiện lỗi cấu hình sớm thay vì nhận lỗi tối nghĩa từ MinIO.
     */
    private String defaultBucket() {
        if (!StringUtils.hasText(properties.getBucket())) {
            throw new IllegalStateException(
                    "vcx.minio.bucket chưa được cấu hình - " +
                    "truyền bucket trực tiếp hoặc khai báo mặc định trong application.yml"
            );
        }
        return properties.getBucket();
    }

    /**
     * Đóng gói exception gốc từ MinIO thành ExternalApiException.
     *
     * Mục đích:
     *  - Chuẩn hóa lỗi từ MinIO (bao gồm nhiều loại checked exception)
     *  - Ghi rõ target bị lỗi (ví dụ "report/2024/file.pdf")
     *  - Dùng status 502 = "Bad Gateway" vì lỗi đến từ service bên ngoài
     *
     * @param target Tên bucket hoặc đường dẫn file bị lỗi
     * @param e      Exception gốc từ MinIO
     */
    private ExternalApiException wrap(String target, Exception e) {
        return new ExternalApiException(target, 502, e.getMessage(), e);
    }
}
