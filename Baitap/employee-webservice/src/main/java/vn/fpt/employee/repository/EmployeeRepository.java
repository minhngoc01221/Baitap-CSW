package vn.fpt.employee.repository;

import vn.fpt.employee.model.Employee;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Each operation owns its JDBC connection; statements use bound parameters. */
public final class EmployeeRepository {
    private final String jdbcUrl;

    public EmployeeRepository(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, "sa", "");
    }

    public void initialize() throws SQLException, IOException {
        try (InputStream input = EmployeeRepository.class.getResourceAsStream("/schema.sql")) {
            if (input == null) { throw new IOException("Missing schema.sql"); }
            String schema = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            try (Connection connection = connect(); var statement = connection.createStatement()) {
                statement.execute(schema);
            }
        }
    }

    public Employee[] findAll() throws SQLException {
        List<Employee> employees = new ArrayList<>();
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement("SELECT id, name, salary FROM employees ORDER BY id");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                employees.add(new Employee(result.getInt("id"), result.getString("name"), result.getBigDecimal("salary")));
            }
        }
        return employees.toArray(Employee[]::new);
    }

    public void insert(Employee employee) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO employees (id, name, salary) VALUES (?, ?, ?)")) {
            statement.setInt(1, employee.getId());
            statement.setString(2, employee.getName());
            statement.setBigDecimal(3, employee.getSalary());
            statement.executeUpdate();
        }
    }

    public boolean update(Employee employee) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement("UPDATE employees SET name = ?, salary = ? WHERE id = ?")) {
            statement.setString(1, employee.getName());
            statement.setBigDecimal(2, employee.getSalary());
            statement.setInt(3, employee.getId());
            return statement.executeUpdate() == 1;
        }
    }
}
