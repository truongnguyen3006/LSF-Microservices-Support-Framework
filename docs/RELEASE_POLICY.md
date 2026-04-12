# Release Policy

## 1. Mục tiêu của release policy

Release policy của LSF trong giai đoạn hiện tại không nhằm giả lập một platform vendor process quá nặng. Mục tiêu thực tế hơn là:

- xác định phần nào là public contract;
- xác định kỳ vọng compatibility theo maturity bucket;
- giúp owner và Codex biết khi nào một thay đổi cần release note, deprecation note hoặc owner review.

## 2. Trạng thái release hiện tại

- Repo hiện đang ở chế độ `1.0-SNAPSHOT`.
- Chưa có bằng chứng rằng mọi module nên được release với cùng mức promise.
- Vì vậy release policy phải phân biệt rõ `stable`, `partial support` và `experimental / scaffold`.

## 3. Official Public Surface Và Mức Promise

| Surface | Ví dụ | Mức promise hiện tại |
|---|---|---|
| Maven coordinates | `com.myorg.lsf:*`, BOM `lsf-parent` | public |
| documented property prefixes | `lsf.kafka.*`, `lsf.eventing.*`, `lsf.outbox.*`, `lsf.quota.*`, ... | public |
| adopter-facing annotations / interfaces / DTOs | `@LsfEventHandler`, `LsfPublisher`, `OutboxWriter`, `@LsfHttpClient`, `QuotaReservationFacade`, `LsfSagaOrchestrator` | public, nhưng mức compatibility phụ thuộc maturity của module |
| internal admin HTTP endpoints | `/lsf/outbox/**`, `/lsf/kafka/**` khi starter được bật | public operational surface nội bộ |
| docs contracts | các file trong `docs/` và module README | public |
| internal helper classes / bean names / test fixtures | auto-config details, implementation internals | non-public |
| scaffold assets | `lsf-example`, `lsf-service-template`, `ops/` defaults | scaffold, không có strong compatibility promise |

## 4. Kỳ vọng breaking changes theo maturity

### Stable

Thay đổi trên stable modules nên:

- tránh breaking changes ở public surface nếu không thật sự cần;
- có release note rõ nếu đổi property names, DTO shape, endpoint contract hoặc behavior quan trọng;
- ưu tiên deprecation / migration note trước khi loại bỏ path cũ.

### Partial Support

Thay đổi trên partial modules có thể linh hoạt hơn, nhưng vẫn phải:

- cập nhật docs trong cùng lượt thay đổi;
- nêu rõ use case nào được support sau thay đổi;
- không “im lặng” thay đổi public surface rồi vẫn quảng bá module như stable.

### Experimental / Scaffold

Thay đổi có thể mạnh hơn, miễn là:

- vẫn phục vụ đúng vai trò scaffold / example;
- không bị diễn giải thành production guarantee;
- README/docs tương ứng được cập nhật.

## 5. Release Checklist Tối Thiểu

Trước khi owner coi một snapshot/milestone là release candidate nội bộ, nên có tối thiểu:

1. `mvn -DskipTests verify` pass trên repo framework.
2. Guardrail Java 21 vẫn hoạt động.
3. Các tài liệu trong `docs/` được cập nhật nếu public surface thay đổi.
4. `docs/MODULE_MATURITY.md` phản ánh đúng bucket hiện tại của module bị ảnh hưởng.
5. `docs/COMPATIBILITY.md` được cập nhật nếu consumer checkpoint thay đổi.
6. Các module `stable` bị ảnh hưởng có regression evidence tương xứng.

## 6. Release Notes Nên Nói Gì

Một release note hữu ích cho LSF nên ghi rõ:

- module nào thay đổi;
- concern nào bị ảnh hưởng;
- public surface nào đổi;
- migration action cho adopter;
- module đó thuộc bucket nào;
- compatibility checkpoint với `ecommerce-backend` có thay đổi hay không.

## 7. Chính sách deprecation

Trong trạng thái hiện tại, deprecation policy thực dụng nhất là:

- với `stable` modules: cố gắng có ít nhất một milestone/release note cảnh báo trước khi xóa public path;
- với `partial support`: có thể thay nhanh hơn, nhưng phải có doc note và migration hint;
- với `experimental / scaffold`: chỉ cần cập nhật docs đồng bộ.

## 8. Những gì không nên bị release policy overclaim

- Không tuyên bố binary compatibility đầy đủ cho mọi internal class.
- Không tuyên bố mọi module có cùng quality bar.
- Không tuyên bố “production ready” cho gateway, saga, sync stack hoặc ops assets nếu chưa có checkpoint tương xứng.
- Không coi example/template assets là public API phải ổn định như runtime modules.

## 9. Owner Decisions Needed

- Chốt scheme versioning chính thức sau `1.0-SNAPSHOT`: tiếp tục snapshot nội bộ hay chuyển sang `MAJOR.MINOR.PATCH`.
- Chốt deprecation window cho `stable` modules.
- Chốt release cadence: theo phase, theo release candidate workflow, hay theo manual tag.
- Chốt có cần changelog/release notes chuẩn hóa trong repo hay vẫn ghi theo milestone thủ công.
- Chốt ai có quyền promote module từ `partial support` lên `stable`.
