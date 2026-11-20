# Bài thực hành CSW - Quản lý nhân viên FPT

Bài làm Java SOAP Web Service theo đề ngày 20/11/2026. Có database, lớp Employee, đủ ba phương thức và web client gọi dịch vụ thực tế qua HTTP.

## 1. Chạy nhanh

Yêu cầu: **JDK 17** và **Maven 3.8+**. Lần build đầu cần Internet để tải thư viện.

Mở terminal tại thư mục `employee-webservice`:

```powershell
mvn clean package
java -jar target/employee-webservice.jar
```

Hoặc nhấp đúp `run.bat` trên Windows. Nếu đã có JAR, file này chạy ngay; khi sửa code, chạy lại `mvn clean package` để cập nhật JAR.

- Giao diện: <http://127.0.0.1:8080/>
- WSDL: <http://127.0.0.1:8080/services/employees?wsdl>
- SOAP endpoint: <http://127.0.0.1:8080/services/employees>
- Dừng ứng dụng: `Ctrl+C`.

Không cần cài Tomcat/GlassFish hay database server. Metro JAX-WS xuất bản SOAP endpoint trên HTTP server của JDK; H2 là database SQL nhúng, lưu ra file thật bằng JDBC.

Đổi cổng nếu 8080 đang được dùng:

```powershell
java -Dapp.port=8081 -jar target/employee-webservice.jar
```

Đổi thư mục lưu database:

```powershell
java "-Dapp.dataDir=D:/Baitap/employee-data" -jar target/employee-webservice.jar
```

Ứng dụng mặc định lắng nghe `127.0.0.1` để dùng trên máy. Thuộc tính `app.host` có thể đổi địa chỉ bind.

## 2. Đối chiếu thang điểm

| Yêu cầu | Điểm | Phần thực hiện |
|---|---:|---|
| Tạo bảng database | 1 | `src/main/resources/schema.sql`, tự khởi tạo khi chạy |
| Lớp Employee | 1 | `model/Employee.java`: id, name, salary; constructor, getter/setter, JAXB |
| getEmployees | 2 | `service/EmployeeService.java`: trả về `Employee[]`, sắp theo id |
| addEmployees | 2 | Nhận Employee, kiểm tra dữ liệu, INSERT bằng PreparedStatement |
| updateEmployee | 2 | Nhận Employee, UPDATE theo id, báo lỗi nếu không tồn tại |
| Web application kiểm thử | 5 | `resources/web/`: danh sách, thêm, sửa, tìm kiếm, tải lại và xem SOAP XML |
| Phong cách lập trình | 2 | Tách model/repository/service, try-with-resources, BigDecimal, thông báo lỗi, test HTTP |
| **Tổng** | **15** | |

Đường dẫn Java trong bảng tính từ `src/main/java/vn/fpt/employee/`.

## 3. Database

```sql
CREATE TABLE IF NOT EXISTS employees (
    id INTEGER PRIMARY KEY CHECK (id > 0),
    name VARCHAR(100) NOT NULL CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    salary DECIMAL(15, 2) NOT NULL CHECK (salary >= 0)
);
```

- Mặc định database nằm tại `data/employees.mv.db`, tính từ thư mục chạy lệnh.
- JDBC URL: `jdbc:h2:file:./data/employees`; user `sa`, password trống cho bài thực hành local.
- Lần đầu danh sách rỗng để người dùng tự kiểm thử chức năng thêm.
- Dữ liệu giữ nguyên khi tắt và mở lại ứng dụng.
- `salary` dùng `BigDecimal` và `DECIMAL(15,2)` để lưu tiền chính xác.
- ID do người dùng nhập, phải là số nguyên dương và duy nhất. Khi sửa, ID giữ nguyên.

## 4. Ba phương thức SOAP

Namespace: `http://service.employee.fpt.vn/`. Tên phần tử tham số: `e`.

```java
public Employee[] getEmployees();
public boolean addEmployees(Employee e);
public boolean updateEmployee(Employee e);
```

Hai phương thức ghi trả `success=true` khi thành công. Dữ liệu sai, mã trùng hoặc mã sửa không tồn tại trả SOAP Fault (`HTTP 500`, `Client`). Lỗi database trả SOAP Fault `Server`, không đưa chi tiết SQL lên giao diện.

Ví dụ request thêm nhân viên:

```xml
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
               xmlns:emp="http://service.employee.fpt.vn/">
  <soap:Body>
    <emp:addEmployees>
      <e><id>1001</id><name>Nguyễn Văn An</name><salary>15000000.00</salary></e>
    </emp:addEmployees>
  </soap:Body>
</soap:Envelope>
```

Ba file XML có sẵn trong `requests/`. Dùng Postman: method `POST`, URL endpoint ở trên, Body → raw → XML, header `Content-Type: text/xml; charset=utf-8`, `SOAPAction: ""`.

Hoặc trên Windows, chạy lần lượt khi server đang mở:

```powershell
curl.exe -H "Content-Type: text/xml; charset=utf-8" --data-binary "@requests/addEmployees.xml" http://127.0.0.1:8080/services/employees
curl.exe -H "Content-Type: text/xml; charset=utf-8" --data-binary "@requests/getEmployees.xml" http://127.0.0.1:8080/services/employees
curl.exe -H "Content-Type: text/xml; charset=utf-8" --data-binary "@requests/updateEmployee.xml" http://127.0.0.1:8080/services/employees
```

Web client dùng `fetch()` gửi SOAP XML đến cùng endpoint, đọc response bằng `DOMParser`, hiển thị kết quả bằng `textContent`. Giao diện không truy cập database trực tiếp. Mở mục **Kiểm thử Web Service** để xem request/response gần nhất. Sau thao tác thêm/sửa, giữ lại XML của thao tác ghi để tiện trình bày.

## 5. Kịch bản trình bày bài

1. Chạy ứng dụng, mở giao diện và WSDL.
2. Thêm mã `1001`, tên `Nguyễn Văn An`, lương `15000000`. Nhân viên xuất hiện trong bảng.
3. Thêm mã `1002`, tên `Trần Thị Bình`, lương `17000000.50` để kiểm tra danh sách nhiều phần tử.
4. Nhấn **Chỉnh sửa** ở dòng 1001, đổi lương thành `18000000`, chọn **Lưu thay đổi**.
5. Mở phần SOAP để xem `updateEmployee` và `success=true`. Nhấn **Tải lại** để kiểm thử `getEmployees`.
6. Thử thêm trùng 1001; giao diện hiển thị lỗi và không thay đổi dữ liệu cũ.
7. Kiểm thử SOAP trực tiếp: ID không tồn tại khi sửa, tên trống, lương âm hoặc quá 2 chữ số thập phân đều bị từ chối. HTML cũng kiểm tra dữ liệu đầu vào trước khi gửi.
8. Tắt rồi chạy lại, xác nhận dữ liệu vẫn còn trong database.

## 6. Kiểm thử tự động

```powershell
mvn test
```

Test xuất bản endpoint trên cổng ngẫu nhiên, gửi SOAP bằng HTTP thật, kiểm tra WSDL, danh sách rỗng, thêm/lấy/sửa, tên tiếng Việt và ký tự XML, ID trùng, cập nhật mã không tồn tại, dữ liệu không hợp lệ, lương bằng 0 và lưu dữ liệu qua lần khởi động lại. Database test tách biệt với dữ liệu chạy ứng dụng.

## 7. Công nghệ và tài liệu

- Java 17, Jakarta XML Web Services / Metro 4.0.3, Maven.
- H2 2.3.232, JDBC, JUnit Jupiter 5.11.4.
- HTML/CSS/JavaScript thuần, không cần Node.js hoặc CDN.
- [Tài liệu Metro: xuất bản endpoint](https://eclipse-ee4j.github.io/metro-jax-ws/4.0.0/docs/ch03.html).
- [Tài liệu H2: database URL và embedded mode](https://h2database.com/html/features.html).

Phạm vi bài thực hành: ba chức năng trong đề; chưa có đăng nhập, phân quyền hay triển khai production.
