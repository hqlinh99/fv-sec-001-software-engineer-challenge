# Campaign Aggregator CLI Tool

Công cụ dòng lệnh (CLI) Java để tổng hợp dữ liệu quảng cáo từ file CSV lớn và tạo báo cáo Top 10 campaigns theo CTR (
Click-Through Rate) và CPA (Cost Per Acquisition).

## Tính năng chính

- **Xử lý file CSV lớn**: Tối ưu hóa bộ nhớ để xử lý file CSV với hàng triệu dòng dữ liệu
- **Tổng hợp dữ liệu**: Gộp tất cả metrics theo campaign_id
- **Báo cáo Top 10 CTR**: Campaigns có tỷ lệ click cao nhất
- **Báo cáo Top 10 CPA**: Campaigns có chi phí conversion thấp nhất
- **Hiển thị thông tin memory usage**: Theo dõi sử dụng bộ nhớ trong quá trình xử lý

## Yêu cầu hệ thống

- **Java**: Java 17+
- **Maven**: Apache Maven 3.6+
- **RAM**: Tối thiểu 8MB

## Dependencies

### Runtime Dependencies
- **Zero external dependencies** ✅
- Chỉ sử dụng Java Standard Library (built-in)
- Không cần cài đặt thêm thư viện nào cho runtime

### Development Dependencies
- **JUnit Jupiter 5.13.4** - cho unit testing
- **Maven plugins** - cho build và package

### Ưu điểm của minimal dependencies:
- **Lightweight**: Không có dependency conflicts
- **Fast startup**: Không cần load external libraries
- **Easy deployment**: Chỉ cần Java runtime
- **Security**: Giảm thiểu attack surface
- **Maintenance**: Ít cập nhật dependencies

## Cài đặt và Build

### 1. Clone repository

```bash
git clone -b main https://github.com/hqlinh99/fv-sec-001-software-engineer-challenge.git
cd fv-sec-001-software-engineer-challenge
```

### 2. Build project với Maven

```bash
# Build project
./mvnw clean compile

# Hoặc build và tạo JAR file
./mvnw clean package
```

**Lưu ý:** Nếu gặp lỗi `./mvnw: Permission denied` trên Linux/Mac:

```bash
# Cấp quyền thực thi cho Maven wrapper
chmod +x ./mvnw

# Hoặc chạy trực tiếp với bash
bash ./mvnw clean compile
```

### 3. Kiểm tra build thành công

```bash
# Chạy unit tests
./mvnw test
```

## Sử dụng

### Format dữ liệu đầu vào

File CSV input cần có 6 cột theo thứ tự sau (có thể có hoặc không có header row):

```csv
campaign_id,date,impressions,clicks,spend,conversions
CMP001,2025-01-01,12000,300,45.50,12
CMP002,2025-01-01,15000,450,78.25,18
CMP003,2025-01-02,8500,210,32.90,8
CMP001,2025-01-02,11500,285,42.80,11
CMP004,2025-01-01,20000,580,95.60,22
```

**Mô tả các cột:**
- `campaign_id`: ID của campaign (string)
- `date`: Thời gian - không sử dụng trong tính toán (string) 
- `impressions`: Số lần hiển thị quảng cáo (integer)
- `clicks`: Số lần click (integer) 
- `spend`: Chi phí (float)
- `conversions`: Số lượng chuyển đổi (integer)


### Syntax cơ bản

```bash
java -cp target/classes org.linhhq.CampaignAggregator --input <path_to_csv> --output <output_directory>
```

### Các tham số

| Tham số          | Bắt buộc | Mô tả                                    |
|------------------|----------|------------------------------------------|
| `--input <path>` | ✅        | Đường dẫn đến file CSV input             |
| `--output <dir>` | ✅        | Thư mục để lưu file kết quả              |
| `--no-header`    | ❌        | Sử dụng nếu file CSV không có header row |

### Ví dụ sử dụng

#### 1. Sử dụng cơ bản

```bash
java -cp target/classes org.linhhq.CampaignAggregator --input ad_data.csv --output ./reports
```

#### 2. Với file CSV không có header

```bash
java -cp target/classes org.linhhq.CampaignAggregator --input ad_data.csv --output ./reports --no-header
```

#### 3. Sử dụng JAR file (nếu đã build)

```bash
java -jar target/fv-sec-001-software-engineer-challenge-1.0.jar --input ad_data.csv --output ./reports
```

### Kết quả output

Tool sẽ tạo ra 2 file CSV trong thư mục output:

#### 1. `top10_ctr.csv`

Top 10 campaigns có CTR cao nhất (chỉ tính campaigns có impressions > 0)

#### 2. `top10_cpa.csv`

Top 10 campaigns có CPA thấp nhất (chỉ tính campaigns có conversions > 0)

### Format file kết quả

Cả hai file đều có các cột:

```csv
campaign_id,total_impressions,total_clicks,total_spend,total_conversions,CTR,CPA
```

- **CTR** = clicks / impressions (làm tròn 4 chữ số thập phân)
- **CPA** = spend / conversions (làm tròn 2 chữ số thập phân)

## Tối ưu hóa Performance

### 1. Tăng memory heap

```bash
# Cho file > 1GB
java -Xms256M -Xmx256M -cp target/classes org.linhhq.CampaignAggregator --input big_file.csv --output ./reports
```

### 2. Sử dụng SSD

- Lưu file input và output trên ổ SSD để tăng tốc độ I/O

### 3. Giám sát memory usage

Tool sẽ tự động hiển thị thông tin memory usage:

```
[Start] Used: 1.00 MB | Total: 8.00 MB | Max: 8.00 MB | Elapsed: 0.000 s
[End] Used: 1.92 MB | Total: 8.00 MB | Max: 8.00 MB | Elapsed: 34.852 s
```

## Xử lý lỗi

### Các lỗi thường gặp:

1. **File không tồn tại**
   ```
   Error: Input file not found: data.csv
   ```
   ➜ Kiểm tra đường dẫn file input

2. **Không đủ memory**
   ```
   OutOfMemoryError: Java heap space
   ```
   ➜ Tăng heap size với `-Xmx` parameter

3. **Dữ liệu không đúng format**
   ```
   Warning: Skipping malformed line 1234 (expected 6 columns, got 4)
   ```
   ➜ Kiểm tra format dữ liệu CSV

4. **Thiếu tham số bắt buộc**
   ```
   Error: --input is required
   ```
   ➜ Cung cấp đầy đủ các tham số `--input` và `--output`

## Development

### Structure project

```
src/
├── main/java/org/linhhq/
│   ├── CampaignAggregator.java  # Main CLI class
│   └── CampaignStats.java       # Data model class
└── test/java/
    └── CampaignAggregatorTest.java  # Unit tests
pom.xml                          # Maven configuration (minimal dependencies)
```

### Dependencies trong pom.xml

```xml
<dependencies>
    <!-- Chỉ có 1 test dependency -->
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter-engine</artifactId>
        <version>5.13.4</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

## License

Dự án này được phát triển cho mục đích đánh giá kỹ thuật.
