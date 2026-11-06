
package com.student;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/students")
public class StudentServlet extends HttpServlet {

    private void sendJson(HttpServletResponse response, int status,
                          String json) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(json);
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }

    private String readBody(HttpServletRequest request)
            throws IOException {
        StringBuilder body = new StringBuilder();
        try (var reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
        }
        return body.toString();
    }

    // Simple JSON field reader for this form's string values.
    private String getJsonValue(String json, String key) {
        String marker = "\"" + key + "\"";
        int keyPos = json.indexOf(marker);
        if (keyPos < 0) return null;

        int colon = json.indexOf(':', keyPos + marker.length());
        if (colon < 0) return null;

        int start = colon + 1;
        while (start < json.length()
                && Character.isWhitespace(json.charAt(start))) {
            start++;
        }

        if (start >= json.length() || json.charAt(start) != '"') {
            return null;
        }

        StringBuilder result = new StringBuilder();
        boolean escaped = false;

        for (int i = start + 1; i < json.length(); i++) {
            char c = json.charAt(i);

            if (escaped) {
                switch (c) {
                    case 'n' -> result.append('\n');
                    case 'r' -> result.append('\r');
                    case 't' -> result.append('\t');
                    default -> result.append(c);
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return result.toString();
            } else {
                result.append(c);
            }
        }
        return null;
    }

    private boolean valid(String... values) {
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    // GET /students — list all students
    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response)
            throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String sql = "SELECT id, name, register_no, department, email, phone FROM students";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            StringBuilder json = new StringBuilder("[");
            boolean first = true;

            while (rs.next()) {
                if (!first) json.append(",");
                json.append("{")
                    .append("\"id\":").append(rs.getInt("id")).append(",")
                    .append("\"name\":\"").append(escape(rs.getString("name"))).append("\",")
                    .append("\"register_no\":\"").append(escape(rs.getString("register_no"))).append("\",")
                    .append("\"department\":\"").append(escape(rs.getString("department"))).append("\",")
                    .append("\"email\":\"").append(escape(rs.getString("email"))).append("\",")
                    .append("\"phone\":\"").append(escape(rs.getString("phone"))).append("\"")
                    .append("}");
                first = false;
            }

            json.append("]");
            sendJson(response, 200, json.toString());

        } catch (SQLException e) {
            getServletContext().log("Failed to load student records", e);
            sendJson(response, 500,
                    "{\"error\":\"Unable to retrieve student records\"}");
        }
    }

    // POST /students — register a student
    @Override
    protected void doPost(HttpServletRequest request,
                          HttpServletResponse response)
            throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String body = readBody(request);
        String name = getJsonValue(body, "name");
        String registerNo = getJsonValue(body, "register_no");
        String department = getJsonValue(body, "department");
        String email = getJsonValue(body, "email");
        String phone = getJsonValue(body, "phone");

        if (!valid(name, registerNo, department, email, phone)) {
            sendJson(response, 400,
                    "{\"error\":\"All fields are required\"}");
            return;
        }

        String sql = "INSERT INTO students "
                + "(name, register_no, department, email, phone) "
                + "VALUES (?, ?, ?, ?, ?)";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, name.trim());
            ps.setString(2, registerNo.trim());
            ps.setString(3, department.trim());
            ps.setString(4, email.trim());
            ps.setString(5, phone.trim());
            ps.executeUpdate();

            sendJson(response, 201,
                    "{\"message\":\"Student registered successfully\"}");

        } catch (SQLException e) {
            getServletContext().log("Failed to register student", e);
            sendJson(response, 500,
                    "{\"error\":\"Registration failed. Check database columns and duplicate register number.\"}");
        }
    }

    // PUT /students?id=1 — update a student
    @Override
    protected void doPut(HttpServletRequest request,
                         HttpServletResponse response)
            throws IOException {

        String id = request.getParameter("id");
        if (id == null || !id.matches("\\d+")) {
            sendJson(response, 400, "{\"error\":\"Valid student ID required\"}");
            return;
        }

        String body = readBody(request);
        String name = getJsonValue(body, "name");
        String registerNo = getJsonValue(body, "register_no");
        String department = getJsonValue(body, "department");
        String email = getJsonValue(body, "email");
        String phone = getJsonValue(body, "phone");

        if (!valid(name, registerNo, department, email, phone)) {
            sendJson(response, 400, "{\"error\":\"All fields are required\"}");
            return;
        }

        String sql = "UPDATE students SET name=?, register_no=?, "
                + "department=?, email=?, phone=? WHERE id=?";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, name.trim());
            ps.setString(2, registerNo.trim());
            ps.setString(3, department.trim());
            ps.setString(4, email.trim());
            ps.setString(5, phone.trim());
            ps.setInt(6, Integer.parseInt(id));

            int updated = ps.executeUpdate();
            if (updated == 0) {
                sendJson(response, 404, "{\"error\":\"Student not found\"}");
            } else {
                sendJson(response, 200, "{\"message\":\"Student updated successfully\"}");
            }

        } catch (SQLException e) {
            getServletContext().log("Failed to update student", e);
            sendJson(response, 500, "{\"error\":\"Student update failed\"}");
        }
    }

    // DELETE /students?id=1 — delete a student
    @Override
    protected void doDelete(HttpServletRequest request,
                            HttpServletResponse response)
            throws IOException {

        String id = request.getParameter("id");
        if (id == null || !id.matches("\\d+")) {
            sendJson(response, 400, "{\"error\":\"Valid student ID required\"}");
            return;
        }

        String sql = "DELETE FROM students WHERE id=?";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, Integer.parseInt(id));
            int deleted = ps.executeUpdate();

            if (deleted == 0) {
                sendJson(response, 404, "{\"error\":\"Student not found\"}");
            } else {
                sendJson(response, 200, "{\"message\":\"Student deleted successfully\"}");
            }

        } catch (SQLException e) {
            getServletContext().log("Failed to delete student", e);
            sendJson(response, 500, "{\"error\":\"Student deletion failed\"}");
        }
    }
}