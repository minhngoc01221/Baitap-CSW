package vn.fpt.employee;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Document;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.InputSource;
import static org.junit.jupiter.api.Assertions.*;

/** Integration tests call the published service over HTTP, not Java methods. */
class EmployeeServiceTest {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private Application app;

    @BeforeEach
    void start() throws Exception {
        app = Application.start("127.0.0.1", 0, "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    }

    @AfterEach
    void stop() { app.close(); }

    private String base(Application application) { return "http://127.0.0.1:" + application.getPort(); }

    private HttpResponse<String> call(Application application, String operation, String body) throws Exception {
        String xml = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:emp=\"http://service.employee.fpt.vn/\">"
                + "<soap:Body><emp:" + operation + ">" + body + "</emp:" + operation + "></soap:Body></soap:Envelope>";
        return client.send(HttpRequest.newBuilder(URI.create(base(application) + "/services/employees"))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "text/xml; charset=utf-8")
                .header("SOAPAction", "\"\"").POST(HttpRequest.BodyPublishers.ofString(xml)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private String employee(int id, String name, String salary) {
        return "<e><id>" + id + "</id><name>" + name + "</name><salary>" + salary + "</salary></e>";
    }

    private Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private String text(Document document, String element) {
        return document.getElementsByTagNameNS("*", element).item(0).getTextContent();
    }

    @Test
    void publishesWsdlAndBrowserClient() throws Exception {
        var wsdl = client.send(HttpRequest.newBuilder(URI.create(base(app) + "/services/employees?wsdl")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, wsdl.statusCode());
        Document document = parse(wsdl.body());
        var operations = document.getElementsByTagNameNS("http://schemas.xmlsoap.org/wsdl/", "operation");
        var names = new java.util.HashSet<String>();
        for (int i = 0; i < operations.getLength(); i++) names.add(operations.item(i).getAttributes().getNamedItem("name").getNodeValue());
        assertEquals(java.util.Set.of("getEmployees", "addEmployees", "updateEmployee"), names);
        var page = client.send(HttpRequest.newBuilder(URI.create(base(app) + "/")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains("Quản lý nhân viên"));
        for (String asset : new String[]{"/app.js", "/style.css"}) {
            assertEquals(200, client.send(HttpRequest.newBuilder(URI.create(base(app) + asset)).GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode());
        }
    }

    @Test
    void returnsEmptyArrayBeforeAdding() throws Exception {
        var response = call(app, "getEmployees", "");
        assertEquals(200, response.statusCode());
        assertEquals(0, parse(response.body()).getElementsByTagNameNS("*", "employee").getLength());
    }

    @Test
    void addsReadsAndUpdatesEmployeeThroughSoap() throws Exception {
        var add = call(app, "addEmployees", employee(1001, "  Nguyễn &amp; An &lt;IT&gt;  ", "15000000.25"));
        assertEquals(200, add.statusCode());
        assertEquals("true", text(parse(add.body()), "success"));
        Document list = parse(call(app, "getEmployees", "").body());
        assertEquals(1, list.getElementsByTagNameNS("*", "employee").getLength());
        assertEquals("Nguyễn & An <IT>", text(list, "name"));
        assertEquals("15000000.25", text(list, "salary"));
        var update = call(app, "updateEmployee", employee(1001, "Trần Bình", "18000000.50"));
        assertEquals(200, update.statusCode());
        assertEquals("true", text(parse(update.body()), "success"));
        Document updated = parse(call(app, "getEmployees", "").body());
        assertEquals("Trần Bình", text(updated, "name"));
        assertEquals("18000000.50", text(updated, "salary"));
        assertEquals(1, updated.getElementsByTagNameNS("*", "employee").getLength());
    }

    @Test
    void rejectsDuplicateWithoutOverwritingExistingEmployee() throws Exception {
        call(app, "addEmployees", employee(10, "Original", "100"));
        var duplicate = call(app, "addEmployees", employee(10, "Replacement", "200"));
        assertEquals(500, duplicate.statusCode());
        assertTrue(text(parse(duplicate.body()), "faultstring").contains("đã tồn tại"));
        assertEquals("Original", text(parse(call(app, "getEmployees", "").body()), "name"));
    }

    @Test
    void updatingMissingEmployeeReturnsFaultAndDoesNotInsert() throws Exception {
        var response = call(app, "updateEmployee", employee(999, "Missing", "0"));
        assertEquals(500, response.statusCode());
        assertTrue(text(parse(response.body()), "faultstring").contains("Không tìm thấy"));
        assertEquals(0, parse(call(app, "getEmployees", "").body()).getElementsByTagNameNS("*", "employee").getLength());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<e><id>0</id><name>An</name><salary>100</salary></e>",
            "<e><id>-1</id><name>An</name><salary>100</salary></e>",
            "<e><id>1</id><name>   </name><salary>100</salary></e>",
            "<e><id>1</id><salary>100</salary></e>",
            "<e><id>1</id><name>An</name><salary>-1</salary></e>",
            "<e><id>1</id><name>An</name><salary>1.001</salary></e>",
            "<e><id>1</id><name>An</name><salary>10000000000000</salary></e>",
            "<e><id>1</id><name>An</name></e>",
            ""
    })
    void rejectsInvalidEmployeeForBothMutations(String input) throws Exception {
        for (String method : new String[]{"addEmployees", "updateEmployee"}) {
            var response = call(app, method, input);
            assertEquals(500, response.statusCode());
            assertEquals(1, parse(response.body()).getElementsByTagNameNS("*", "Fault").getLength());
        }
        assertEquals(0, parse(call(app, "getEmployees", "").body()).getElementsByTagNameNS("*", "employee").getLength());
    }

    @Test
    void acceptsZeroSalaryAndRejectsOverlongName() throws Exception {
        assertEquals(200, call(app, "addEmployees", employee(1, "Zero", "0")).statusCode());
        assertEquals(500, call(app, "addEmployees", employee(2, "A".repeat(101), "100")).statusCode());
        assertEquals("0.00", text(parse(call(app, "getEmployees", "").body()), "salary"));
    }

    @Test
    void keepsDataAfterRestart(@TempDir Path directory) throws Exception {
        String url = "jdbc:h2:file:" + directory.resolve("employees").toString().replace('\\', '/');
        try (Application first = Application.start("127.0.0.1", 0, url)) {
            assertEquals(200, call(first, "addEmployees", employee(42, "Persistent employee", "123.45")).statusCode());
        }
        try (Application restarted = Application.start("127.0.0.1", 0, url)) {
            Document list = parse(call(restarted, "getEmployees", "").body());
            assertEquals("42", text(list, "id"));
            assertEquals("123.45", text(list, "salary"));
        }
    }
}
