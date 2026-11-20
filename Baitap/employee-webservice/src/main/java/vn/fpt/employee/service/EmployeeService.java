package vn.fpt.employee.service;

import jakarta.jws.WebMethod;
import jakarta.jws.WebParam;
import jakarta.jws.WebResult;
import jakarta.jws.WebService;
import jakarta.xml.ws.soap.SOAPFaultException;
import jakarta.xml.soap.SOAPConstants;
import jakarta.xml.soap.SOAPException;
import jakarta.xml.soap.SOAPFactory;
import vn.fpt.employee.model.Employee;
import vn.fpt.employee.repository.EmployeeRepository;
import javax.xml.namespace.QName;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebService(serviceName = "EmployeeService", portName = "EmployeeServicePort",
        name = "EmployeeServicePortType", targetNamespace = EmployeeService.NAMESPACE)
public class EmployeeService {
    public static final String NAMESPACE = "http://service.employee.fpt.vn/";
    private static final Logger LOGGER = Logger.getLogger(EmployeeService.class.getName());
    private final EmployeeRepository repository;

    public EmployeeService(EmployeeRepository repository) { this.repository = repository; }

    @WebMethod(operationName = "getEmployees")
    @WebResult(name = "employee")
    public Employee[] getEmployees() {
        try {
            return repository.findAll();
        } catch (SQLException exception) {
            throw databaseFault(exception);
        }
    }

    @WebMethod(operationName = "addEmployees")
    @WebResult(name = "success")
    public boolean addEmployees(@WebParam(name = "e") Employee employee) {
        Employee valid = validate(employee);
        try {
            repository.insert(valid);
            return true;
        } catch (SQLException exception) {
            if ("23505".equals(exception.getSQLState())) {
                throw fault("Mã nhân viên đã tồn tại.", true);
            }
            throw databaseFault(exception);
        }
    }

    @WebMethod(operationName = "updateEmployee")
    @WebResult(name = "success")
    public boolean updateEmployee(@WebParam(name = "e") Employee employee) {
        Employee valid = validate(employee);
        try {
            if (!repository.update(valid)) { throw fault("Không tìm thấy nhân viên cần cập nhật.", true); }
            return true;
        } catch (SQLException exception) {
            throw databaseFault(exception);
        }
    }

    private Employee validate(Employee employee) {
        if (employee == null) { throw fault("Vui lòng cung cấp thông tin nhân viên.", true); }
        if (employee.getId() <= 0) { throw fault("Mã nhân viên phải là số nguyên dương.", true); }
        String name = employee.getName() == null ? "" : employee.getName().strip();
        if (name.isEmpty() || name.length() > 100) {
            throw fault("Tên nhân viên phải có từ 1 đến 100 ký tự.", true);
        }
        BigDecimal salary = employee.getSalary();
        if (salary == null || salary.signum() < 0 || salary.compareTo(new BigDecimal("9999999999999.99")) > 0) {
            throw fault("Lương phải nằm trong khoảng 0 đến 9999999999999.99.", true);
        }
        try {
            salary = salary.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw fault("Lương chỉ được có tối đa 2 chữ số thập phân.", true);
        }
        return new Employee(employee.getId(), name, salary);
    }

    private SOAPFaultException databaseFault(SQLException exception) {
        LOGGER.log(Level.SEVERE, "Database operation failed", exception);
        return fault("Không thể truy cập cơ sở dữ liệu. Vui lòng thử lại.", false);
    }

    private SOAPFaultException fault(String message, boolean clientError) {
        try {
            return new SOAPFaultException(SOAPFactory.newInstance().createFault(message,
                    new QName(SOAPConstants.URI_NS_SOAP_ENVELOPE, clientError ? "Client" : "Server")));
        } catch (SOAPException exception) {
            throw new IllegalStateException("Cannot create SOAP fault", exception);
        }
    }
}
