# Job kiểm tra điều kiện & trigger sinh báo cáo đầu ra

Ngày rà soát mã nguồn: **11/11/2026**.

## a. Thông tin chung

- **Loại:** Luồng nội bộ kiểm tra điều kiện, chuẩn bị instance/version và phát sự kiện sinh báo cáo đầu ra.
- **Trigger tự động:** Sau khi import/re-import file đầu vào thành công, transaction lưu file và cập nhật input instance sang `COMPLETED` đã commit.
- **Điểm gọi:** `ReportImportServiceImpl.importFile()` gọi `OutputGenerationCoordinator.prepareAndPublish(inputDefinitionId, weekStartDate, "system")`.
- **Bộ điều phối:** `OutputGenerationCoordinator`.
- **Kiểm tra điều kiện:** `OutputReadinessServiceImpl.checkReadiness()`.
- **Chuẩn bị output:** `ReportOutputGenerationTransactionService.prepare()`.
- **Publisher:** `OutputGenerationEventPublisher`.
- **Sự kiện phát ra:** `OutputGenerationPreparedEvent`, sau khi transaction prepare đã commit trạng thái `GENERATING`.
- **Listener tiếp nhận:** `OutputGenerationEventListener.onGenerationPrepared()` với `@EventListener` và `@Async`.
- **Execution:** Tra cứu phụ thuộc, kiểm tra điều kiện và prepare/publish chạy đồng bộ trong luồng gọi; xử lý sinh file chạy bất đồng bộ ở listener.
- **Phạm vi output hiện hỗ trợ:** `2001` – Báo cáo Word tuần VCX; `2002`, `2003` – các phụ lục Excel.
- **Cách kích hoạt bổ sung:** API `POST /api/reports/outputs/generate` gọi `prepareAndPublishOutputs()`; mô tả tại mục c.

**Lưu ý về tên “Job”:** Trong mã nguồn được rà soát, luồng này không có `@Scheduled` hoặc cron riêng. Hệ thống kiểm tra các output phụ thuộc khi có input import thành công hoặc khi có yêu cầu generate qua API.

**Dữ liệu đầu vào của luồng tự động**

| STT | Trường dữ liệu | Kiểu dữ liệu | Mô tả |
|---|---|---|---|
| 1 | inputDefinitionId | Long | ID definition của file đầu vào vừa import/re-import thành công. Tham số tại điểm gọi có tên `definitionId`. |
| 2 | weekStartDate | LocalDate | Ngày thứ Hai bắt đầu tuần báo cáo, lấy từ lần import; dùng để kiểm tra nguồn và chuẩn bị output cùng kỳ. |
| 3 | actor | String | Luồng tự động truyền cố định `system`, không truyền actor của người import. |

**Kết quả kiểm tra điều kiện – OutputReadinessResult**

| STT | Trường dữ liệu | Kiểu dữ liệu | Mô tả |
|---|---|---|---|
| 1 | configured | boolean | `true` khi tìm được ít nhất một source definition qua các block và source mapping đang active. Không khẳng định toàn bộ cấu hình sinh file đã hợp lệ. |
| 2 | readyToGenerate | boolean | Có nguồn cấu hình và đáp ứng quy tắc riêng của output: `2001` được phép thiếu nguồn; output còn lại yêu cầu tất cả nguồn `COMPLETED`. |
| 3 | missingSourceIds | List<Long> | Các source definition chưa có instance đúng tuần hoặc có instance với trạng thái khác `COMPLETED`; mỗi ID chỉ xuất hiện một lần. |

**Dữ liệu sự kiện – OutputGenerationPreparedEvent**

| STT | Trường dữ liệu | Kiểu dữ liệu | Mô tả |
|---|---|---|---|
| 1 | outputDefinitionId | Long | ID báo cáo đầu ra cần sinh. |
| 2 | weekStartDate | LocalDate | Tuần báo cáo của output, cùng tuần với input vừa import. |
| 3 | instanceId | Long | ID output instance đã được prepare và commit `GENERATING`. |
| 4 | versionNo | Integer | Version dự kiến tạo: `1` nếu chưa có file lịch sử, ngược lại bằng version lớn nhất hiện có cộng `1`. |
| 5 | regeneration | boolean | `true` nếu output instance đã có ít nhất một bản ghi file lịch sử; không xác định theo `currentFileId`. |
| 6 | actor | String | Người/tác nhân thực hiện; trong luồng tự động là `system`. |

## b. Xử lý luồng tự động sau import/re-import

Các mã Q1–Q8 trong bảng tham chiếu SQL tương đương tại mục b.2. SQL minh họa thao tác JPA, không phải bản ghi SQL thực thi thực tế.

### b.1. Trình tự xử lý

| Bước | Mô tả |
|---|---|
| 1 | `ReportImportServiceImpl.importFile()` hoàn tất upload file đầu vào và gọi `ReportImportTransactionService.commit()`. Transaction này lưu `RptFile`, gán `input.currentFileId`, chuyển input sang `COMPLETED` và ghi audit import. Chỉ sau khi commit thành công mới đi tiếp sang bước kiểm tra output. Import không đạt validation hoặc upload/commit thất bại không đi tới bước này. |
| 2 | Service gọi `outputGenerationCoordinator.prepareAndPublish(inputDefinitionId, weekStartDate, "system")`. Việc kiểm tra và prepare output vẫn chạy trong luồng xử lý import, trước khi controller trả response. |
| 3 | Coordinator lấy danh sách output phụ thuộc trực tiếp vào input vừa import bằng `findActiveOutputDefinitionIdsBySourceDefinitionId()`. Chỉ lấy mapping có `status = 1` và block có `status = 1`; dùng `DISTINCT` để mỗi output chỉ xuất hiện một lần. **SQL Q1.** |
| 4 | Nếu không có output phụ thuộc thì kết thúc kiểm tra, ghi log hoàn tất. Nếu có, duyệt lần lượt từng output; không quét mọi output trong hệ thống và không tự chuyển sang tuần khác. |
| 5 | Với mỗi output, kiểm tra bộ sinh có hỗ trợ hay không. ID `2001` được chấp nhận trực tiếp cho bộ sinh Word; các ID khác phải được `ExcelOutputGenerator.supports()` chấp nhận, hiện là `2002`, `2003`. Output chưa hỗ trợ được ghi log và bỏ qua. |
| 6 | Gọi `readinessService.checkReadiness(outputDefinitionId, weekStartDate)`. Service tìm `RptDefinition` theo ID. Nếu không tồn tại, phát sinh `ResourceNotFoundException` với thông báo `Output definition không tồn tại: id={outputDefinitionId}`. **SQL Q2.** |
| 7 | Kiểm tra definition đang active (`status = 1`), `direction = OUTPUT`, `nodeType = FILE`, `sourceMode = GENERATE`. Nếu không đạt, phát sinh `InvalidReportStateException`: `Definition không phải output generate đang hoạt động: id={outputDefinitionId}`. |
| 8 | Lấy các `RptOutputBlockDef` active của output, sắp xếp theo `sortOrder`. **SQL Q3.** |
| 9 | Với từng block, lấy các `RptOutputSourceMap` active theo `sortOrder`. Gom `sourceDefinitionId` thành danh sách không trùng; một nguồn được dùng ở nhiều block/sheet vẫn chỉ được kiểm tra một lần. **SQL Q4.** |
| 10 | Nếu danh sách source definition rỗng thì trả `configured = false`, `readyToGenerate = false`, `missingSourceIds = []`. Coordinator ghi log chưa đủ input và bỏ qua output; chưa tạo hoặc cập nhật output instance. Quy tắc này áp dụng cả với `2001`. |
| 11 | Lấy instance của toàn bộ source definition trong đúng `weekStartDate` bằng một truy vấn `findByWeekStartDateAndDefinitionIdIn()`, rồi tạo bảng tra trạng thái theo definition ID. **SQL Q5.** |
| 12 | Với mỗi nguồn: nếu không có instance hoặc trạng thái khác `COMPLETED` thì thêm ID vào `missingSourceIds`. Các trạng thái `EMPTY`, `PROCESSING`, `REJECTED`, `ERROR`, `GENERATING`, `STALE` đều chưa đạt điều kiện nguồn hoàn thành. |
| 13 | Nếu đã có ít nhất một nguồn cấu hình thì trả `configured = true`. Với `2001`, đặt `readyToGenerate = true` kể cả khi `missingSourceIds` còn phần tử. Với output khác, chỉ đặt `readyToGenerate = true` khi `missingSourceIds` rỗng. |
| 14 | Coordinator kiểm tra `readyToGenerate`. Nếu `false`, ghi log ID output, tuần và danh sách nguồn còn thiếu rồi chuyển sang output tiếp theo. Nếu `true`, gọi `transactionService.prepare(outputDefinitionId, weekStartDate, actor)`. |
| 15 | Trong transaction prepare, tìm output instance theo cặp `definitionId + weekStartDate` với `PESSIMISTIC_WRITE`. Bản ghi đã tồn tại được khóa để kiểm tra và cập nhật. **SQL Q6.** |
| 16 | Nếu instance đang `GENERATING` và `updatedAt` là `null`, hoặc `updatedAt` lớn hơn thời điểm hiện tại trừ 5 phút, dừng prepare với `InvalidReportStateException`: `Output đang được generate`. Coordinator ghi log bỏ qua output này và tiếp tục output kế tiếp. |
| 17 | Nếu instance đang `GENERATING` nhưng `updatedAt` đã cách hiện tại ít nhất 5 phút thì cho phép prepare lại. Các trạng thái khác cũng được phép prepare ngay, kể cả `COMPLETED`. Mốc 5 phút được kiểm tra khi có lần gọi mới; không phải tác vụ tự chạy lại hoặc cơ chế hủy worker cũ. |
| 18 | Nếu chưa có output instance thì tạo `RptInstance` với `definitionId`, `weekStartDate`, `createdBy = actor`. Nếu đã có thì dùng lại instance hiện tại. |
| 19 | Gán `status = GENERATING (5)`, `updatedBy = actor`, `updatedAt = now`, rồi lưu instance. Khi tạo mới, các trường audit mặc định được entity điền lúc persist. Khi prepare lại, giữ `currentFileId` và các version cũ. **SQL Q7.** |
| 20 | Tìm file có `versionNo` lớn nhất của output instance bằng `findTopByReportInstanceIdOrderByVersionNoDesc()`. Nếu chưa có, cấp `versionNo = 1`, `regeneration = false`; nếu đã có, cấp `versionNo = latest.versionNo + 1`, `regeneration = true`. Bước này chưa tạo bản ghi `RptFile` mới. **SQL Q8.** |
| 21 | Prepare trả về `GenerationAllocation(instanceId, versionNo, regeneration)`. Khi lời gọi qua transaction proxy hoàn tất thành công, transaction commit trạng thái `GENERATING`; sau đó coordinator mới tạo event. Nếu prepare thất bại thì transaction rollback và không phát event cho output này. |
| 22 | Tạo `OutputGenerationPreparedEvent` gồm `outputDefinitionId`, `weekStartDate`, `instanceId`, `versionNo`, `regeneration`, `actor` theo bảng dữ liệu ở mục a. |
| 23 | Gọi `eventPublisher.publishPrepared(event)`, bên trong gọi `ApplicationEventPublisher.publishEvent(event)`. Đây là sự kiện nội bộ Spring trong tiến trình ứng dụng. |
| 24 | Nếu việc giao tác vụ bất đồng bộ bị từ chối bằng `TaskRejectedException`, coordinator gọi `transactionService.markError(instanceId, actor)` trong transaction riêng. Instance có `currentFileId` thì chuyển `STALE (6)`; chưa có thì chuyển `ERROR (4)`. Sau đó ném lại exception để nhánh xử lý từng output ghi log lỗi. |
| 25 | Nếu publish không ném lỗi, ghi log đã prepare gồm `outputDefinitionId`, `instanceId`, thời gian prepare và publish. Điều này xác nhận đã chuẩn bị/phát sự kiện, chưa xác nhận file báo cáo đã sinh thành công. |
| 26 | `OutputGenerationEventListener.onGenerationPrepared()` tiếp nhận event bằng `@EventListener` và `@Async`, rồi gọi `ReportOutputGenerationService.execute(event)` trên luồng nền. `2001` đi qua bộ sinh Word và snapshot; `2002`, `2003` đi qua bộ sinh Excel. Chi tiết tạo nội dung file thuộc các tài liệu generate tương ứng. |
| 27 | Coordinator tiếp tục xử lý các output còn lại. Trong nhánh tự động, `InvalidReportStateException` của từng output được ghi log bỏ qua; `RuntimeException` khác trong `prepareAndPublishOne()` được ghi log lỗi. Các lỗi được bắt tại đây không dừng việc xét output kế tiếp. |
| 28 | Sau khi duyệt xong, ghi log hoàn tất coordinator và trả điều khiển về luồng import. Listener có thể bắt đầu trước hoặc sau thời điểm HTTP response được trả; luồng gọi không chờ listener sinh file xong. |

### b.2. SQL tương đương các bước xử lý

**Q1 – Tìm output phụ thuộc input vừa import (bước 3)**

```sql
SELECT DISTINCT b.output_definition_id
FROM rpt_output_source_maps m
JOIN rpt_output_block_defs b ON b.id = m.output_block_id
WHERE m.source_definition_id = :inputDefinitionId
  AND m.status = 1
  AND b.status = 1;
```

Truy vấn này chưa lọc trạng thái của output definition; definition được kiểm tra riêng tại bước 6–7. Truy vấn không quy định thứ tự output trả về.

**Q2 – Lấy definition đầu ra (bước 6)**

```sql
SELECT *
FROM rpt_definitions
WHERE id = :outputDefinitionId;
```

Điều kiện kiểm tra trong Java: `status = 1`, `direction = OUTPUT (1)`, `node_type = FILE (1)`, `source_mode = GENERATE (1)`.

**Q3 – Lấy block active của output (bước 8)**

```sql
SELECT *
FROM rpt_output_block_defs
WHERE output_definition_id = :outputDefinitionId
  AND status = 1
ORDER BY sort_order ASC;
```

**Q4 – Lấy source mapping active của từng block (bước 9)**

```sql
SELECT *
FROM rpt_output_source_maps
WHERE output_block_id = :outputBlockId
  AND status = 1
ORDER BY sort_order ASC;
```

**Q5 – Lấy trạng thái các nguồn trong tuần báo cáo (bước 11)**

```sql
SELECT *
FROM rpt_instances
WHERE week_start_date = :weekStartDate
  AND definition_id IN (:sourceDefinitionIds);
```

Java đối chiếu trạng thái với `COMPLETED (2)`; nguồn không có trong kết quả cũng được đưa vào `missingSourceIds`.

**Q6 – Tìm và khóa output instance theo definition/tuần (bước 15)**

```sql
SELECT *
FROM rpt_instances
WHERE definition_id = :outputDefinitionId
  AND week_start_date = :weekStartDate
FOR UPDATE;
```

`FOR UPDATE` mô tả ý nghĩa khóa `PESSIMISTIC_WRITE`; cú pháp SQL thực tế phụ thuộc dialect cơ sở dữ liệu. Nếu chưa có bản ghi thì không có instance hiện hữu để khóa.

**Q7 – Lưu output instance ở trạng thái GENERATING (bước 18–19)**

Nếu chưa tồn tại:

```sql
INSERT INTO rpt_instances (
    definition_id, week_start_date, status, rejected_by,
    created_at, created_by, updated_at, updated_by
)
VALUES (
    :outputDefinitionId, :weekStartDate, 5, '',
    :createdAt, :actor, :updatedAt, :actor
);
```

Nếu đã tồn tại, các thay đổi chính tương đương:

```sql
UPDATE rpt_instances
SET status = 5,
    updated_by = :actor,
    updated_at = :updatedAt
WHERE id = :instanceId;
```

`createdAt`, `updatedAt` do Java/entity audit cấp. `current_file_id` hiện tại không bị xóa ở bước prepare.

**Q8 – Lấy version file lớn nhất (bước 20)**

```sql
SELECT *
FROM rpt_files
WHERE report_instance_id = :instanceId
ORDER BY version_no DESC
LIMIT 1;
```

## c. Nhánh kích hoạt qua API generate output

API `POST /api/reports/outputs/generate` dùng chung bước prepare và publish với luồng tự động, nhưng chọn danh sách output và xử lý lỗi theo cách sau:

| Bước | Mô tả |
|---|---|
| 1 | Nhận `definitionIds: List<Long>` không bắt buộc, `weekStartDate: LocalDate` bắt buộc, `actor: String` mặc định `system`. Controller kiểm tra `weekStartDate` phải là thứ Hai; nếu sai, báo `weekStartDate phải là ngày Thứ Hai`. |
| 2 | Gọi `OutputGenerationCoordinator.prepareAndPublishOutputs(definitionIds, weekStartDate, actor)`. Nếu `definitionIds` null/rỗng, lấy tất cả definition active có `direction = OUTPUT`, `nodeType = FILE`, `sourceMode = GENERATE`, sắp xếp `sortOrder ASC, id ASC`. |
| 3 | Nếu truyền ID cụ thể, loại ID trùng và giữ thứ tự xuất hiện đầu tiên. Nếu có phần tử null, báo `definitionIds không được chứa giá trị null`. Nếu có ID không tồn tại, dừng trước prepare và báo `Output definition không tồn tại: {ids}`. |
| 4 | Nếu danh sách definition sau tra cứu rỗng, báo `Không có output nào để generate`. Nếu có, gọi `checkReadinessBatch()`; mọi definition phải qua cùng kiểm tra active/OUTPUT/FILE/GENERATE. Definition không hợp lệ làm lời gọi batch thất bại trước khi bắt đầu prepare. |
| 5 | Batch lấy các cặp output/source không trùng qua block và mapping active trong một truy vấn, rồi lấy instance của hợp các source ID trong tuần bằng một truy vấn khi có nguồn. Tính `configured`, `missingSourceIds`, `readyToGenerate` cho từng output theo quy tắc tại mục b. |
| 6 | Sau khi có kết quả readiness, bỏ qua output chưa hỗ trợ hoặc chưa đủ điều kiện. Các output còn lại được prepare và publish lần lượt như bước 15–25 mục b. |
| 7 | Nhánh batch không có lớp bắt lỗi riêng cho từng output như `prepareAndPublishOne()`. Lỗi prepare/publish có thể làm dừng các output chưa đến lượt; các output trước đó đã commit/publish vẫn giữ kết quả, không có transaction bao trùm toàn bộ batch. |
| 8 | Nếu xử lý đồng bộ kết thúc không có exception, API trả thông báo `Đã bắt đầu generate báo cáo đầu ra`. Kết quả này vẫn có thể xảy ra khi mọi output đều bị bỏ qua do chưa đủ nguồn hoặc chưa được hỗ trợ; không phải xác nhận sinh thành công tất cả output. |

SQL chọn toàn bộ output khi không truyền `definitionIds`:

```sql
SELECT *
FROM rpt_definitions
WHERE direction = 1
  AND node_type = 1
  AND source_mode = 1
  AND status = 1
ORDER BY sort_order ASC, id ASC;
```

SQL lấy cấu hình nguồn cho batch:

```sql
SELECT DISTINCT b.output_definition_id, m.source_definition_id
FROM rpt_output_source_maps m
JOIN rpt_output_block_defs b ON b.id = m.output_block_id
WHERE b.output_definition_id IN (:outputDefinitionIds)
  AND m.status = 1
  AND b.status = 1;
```

## d. Kết quả và các điểm cần phân biệt

| Tình huống | Kết quả xử lý |
|---|---|
| Không có output phụ thuộc input vừa import | Kết thúc coordinator, không phát event. |
| Output chưa được bộ sinh hỗ trợ | Ghi log và bỏ qua. |
| Không có source mapping active qua các block active | `configured = false`, `readyToGenerate = false`, không prepare. |
| Output Excel còn nguồn chưa `COMPLETED` | `readyToGenerate = false`, ghi log nguồn thiếu và bỏ qua. |
| Output Word `2001` có nguồn cấu hình nhưng còn nguồn thiếu | `readyToGenerate = true`; cho phép chuyển sang prepare/generate. |
| Output đang `GENERATING`, chưa đủ 5 phút hoặc không có `updatedAt` | Báo `Output đang được generate`; nhánh tự động bỏ qua output này. |
| Output đang `GENERATING`, đã đủ ít nhất 5 phút | Cho phép prepare lại khi có lần kích hoạt mới. |
| Prepare và publish thành công | Output ở `GENERATING`, event đã phát; chưa có file version mới được commit ở bước này. |
| Executor từ chối nhận tác vụ bằng `TaskRejectedException` | Gọi `markError()`: có `currentFileId` → `STALE`; không có → `ERROR`. |
| Worker sinh file, upload và commit thành công | Luồng generate lưu `RptFile`, cập nhật `currentFileId`, chuyển `COMPLETED` và ghi audit. |
| Worker sinh file hoặc commit thất bại | Luồng generate thực hiện dọn dẹp/bù trừ; `markError()` chuyển `STALE` hoặc `ERROR` theo `currentFileId`. Listener ghi log lỗi. |

**Các giới hạn đúng theo mã nguồn hiện tại**

- Readiness chỉ kiểm tra có cấu hình nguồn và trạng thái source instance. Chưa kiểm tra `currentFileId`, metadata `RptFile`, object MinIO, template, sheet, bookmark hoặc tính hợp lệ đầy đủ của từng block. Đạt readiness không bảo đảm generate thành công.
- Ngoại lệ cho Word `2001` là quyền đi tiếp đến generate khi thiếu nguồn. Worker vẫn dùng `ReportOutputGenerationTransactionService.commit()`, hiện yêu cầu tìm thấy instance của từng source mapping khi lưu quan hệ file nguồn. Vì vậy chưa thể hiểu ngoại lệ này là bảo đảm lưu thành công báo cáo trong mọi trường hợp thiếu input.
- Coordinator không đối chiếu version/hash của nguồn với lần generate trước. Nếu output đã `COMPLETED` và có trigger mới, output vẫn có thể được chuẩn bị sinh version tiếp theo.
- Khóa được dùng ở bước prepare output instance; việc đọc readiness và prepare không nằm trong một transaction chung để khóa toàn bộ nguồn.
- Event không chứa snapshot ID/version file nguồn. Việc lựa chọn và kiểm tra file nguồn cụ thể thuộc luồng generate phía sau.
- Mốc 5 phút chỉ mở lại quyền prepare; mã ở đây không dừng worker đang chạy, không có heartbeat và không tự retry sau khi nguồn thay đổi trong lúc output bị bỏ qua vì đang `GENERATING`.
- Publish là sự kiện nội bộ Spring; trong các thành phần đã rà soát chưa có outbox/hàng đợi bền vững hoặc job phục hồi riêng bảo đảm phát lại nếu ứng dụng dừng giữa commit prepare và publish.
- Lớp bắt lỗi từng output chỉ bao quanh `prepareAndPublishOne()`. Lỗi truy vấn danh sách phụ thuộc ở đầu `prepareAndPublish()` vẫn có thể truyền ra luồng import dù transaction lưu input trước đó đã commit.

## e. Mã nguồn đối chiếu

Tài liệu mô tả mã nguồn hiện có trong workspace ngày 11/11/2026, bao gồm nội dung đang chỉnh sửa; chưa xác minh cấu hình database hoặc kết quả chạy trên môi trường triển khai.

| Thành phần | Tệp mã nguồn |
|---|---|
| Điểm kích hoạt sau import | [ReportImportServiceImpl.java](../src/main/java/com/techasians/alertservice/service/impl/ReportImportServiceImpl.java) |
| Điều phối tự động và batch | [OutputGenerationCoordinator.java](../src/main/java/com/techasians/alertservice/service/impl/OutputGenerationCoordinator.java) |
| Kiểm tra readiness | [OutputReadinessServiceImpl.java](../src/main/java/com/techasians/alertservice/service/impl/OutputReadinessServiceImpl.java) |
| Khóa instance, cấp version, cập nhật lỗi | [ReportOutputGenerationTransactionService.java](../src/main/java/com/techasians/alertservice/service/impl/ReportOutputGenerationTransactionService.java) |
| Truy vấn phụ thuộc | [RptOutputSourceMapRepository.java](../src/main/java/com/techasians/alertservice/repository/RptOutputSourceMapRepository.java) |
| Truy vấn và khóa instance | [RptInstanceRepository.java](../src/main/java/com/techasians/alertservice/repository/RptInstanceRepository.java) |
| Phát event | [OutputGenerationEventPublisher.java](../src/main/java/com/techasians/alertservice/service/OutputGenerationEventPublisher.java) |
| Dữ liệu event | [OutputGenerationPreparedEvent.java](../src/main/java/com/techasians/alertservice/event/OutputGenerationPreparedEvent.java) |
| Tiếp nhận event bất đồng bộ | [OutputGenerationEventListener.java](../src/main/java/com/techasians/alertservice/event/OutputGenerationEventListener.java) |
| Xử lý sau event | [ReportOutputGenerationServiceImpl.java](../src/main/java/com/techasians/alertservice/service/impl/ReportOutputGenerationServiceImpl.java) |
| API generate | [ReportController.java](../src/main/java/com/techasians/alertservice/controller/ReportController.java) |

Các test đã đọc để đối chiếu hành vi: `OutputGenerationCoordinatorTest`, `OutputReadinessServiceImplTest`, `ReportOutputGenerationTransactionServiceTest`. Việc rà soát tài liệu không bao gồm chạy lại test ứng dụng.
