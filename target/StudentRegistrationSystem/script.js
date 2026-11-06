
document.addEventListener("DOMContentLoaded", () => {
    const API_URL = "students";

    const form = document.getElementById("studentForm");
    const tableBody = document.getElementById("studentTable");
    const status = document.getElementById("status");
    const clearBtn = document.getElementById("clearBtn");
    const saveBtn = document.getElementById("saveBtn");
    const studentId = document.getElementById("studentId");

    function showStatus(message, isError = false) {
        status.textContent = message;
        status.style.color = isError ? "#b00020" : "#087443";
    }

    async function request(url, options = {}) {
        const response = await fetch(url, options);
        const text = await response.text();

        if (!response.ok) {
            throw new Error(
                `HTTP ${response.status}: ` +
                text.replace(/<[^>]*>/g, " ").trim().slice(0, 200)
            );
        }

        if (!text.trim()) {
            return {};
        }

        try {
            return JSON.parse(text);
        } catch {
            throw new Error(
                "Server returned HTML instead of JSON. Check the servlet URL and server logs."
            );
        }
    }

    // Load student records
    async function loadStudents() {
        try {
            const students = await request(API_URL, {
                method: "GET",
                headers: { Accept: "application/json" }
            });

            if (!Array.isArray(students)) {
                throw new Error("Unexpected response from the server.");
            }

            tableBody.replaceChildren();

            if (students.length === 0) {
                tableBody.innerHTML =
                    '<tr><td colspan="7">No student records found.</td></tr>';
                return;
            }

            students.forEach(student => {
                const row = document.createElement("tr");

                [
                    student.id,
                    student.name,
                    student.register_no ?? student.registerNo,
                    student.department,
                    student.email,
                    student.phone
                ].forEach(value => {
                    const cell = document.createElement("td");
                    cell.textContent = value ?? "";
                    row.appendChild(cell);
                });

                const actionCell = document.createElement("td");
                const editBtn = document.createElement("button");
                editBtn.type = "button";
                editBtn.textContent = "Edit";
                editBtn.addEventListener("click", () => fillForm(student));

                const deleteBtn = document.createElement("button");
                deleteBtn.type = "button";
                deleteBtn.textContent = "Delete";
                deleteBtn.style.marginLeft = "5px";
                deleteBtn.addEventListener("click", () => deleteStudent(student.id));

                actionCell.append(editBtn, deleteBtn);
                row.appendChild(actionCell);
                tableBody.appendChild(row);
            });
        } catch (error) {
            console.error("Load records failed:", error);
            tableBody.innerHTML =
                '<tr><td colspan="7">Unable to load records.</td></tr>';
            showStatus(error.message, true);
        }
    }

    // Register or update a student
    form.addEventListener("submit", async event => {
        event.preventDefault();

        const student = {
            name: document.getElementById("name").value.trim(),
            register_no: document.getElementById("registerNo").value.trim(),
            department: document.getElementById("department").value,
            email: document.getElementById("email").value.trim(),
            phone: document.getElementById("phone").value.trim()
        };

        if (Object.values(student).some(value => !value)) {
            showStatus("Please fill in all required fields.", true);
            return;
        }

        saveBtn.disabled = true;

        try {
            const editing = Boolean(studentId.value);
            const url = editing
                ? `${API_URL}?id=${encodeURIComponent(studentId.value)}`
                : API_URL;

            await request(url, {
                method: editing ? "PUT" : "POST",
                headers: {
                    "Content-Type": "application/json",
                    Accept: "application/json"
                },
                body: JSON.stringify(student)
            });

            showStatus(editing
                ? "Student updated successfully."
                : "Student registered successfully.");

            form.reset();
            studentId.value = "";
            saveBtn.textContent = "Register Student";
            await loadStudents();
        } catch (error) {
            console.error("Save student failed:", error);
            showStatus(error.message, true);
        } finally {
            saveBtn.disabled = false;
        }
    });

    // Fill the form for editing
    function fillForm(student) {
        studentId.value = student.id ?? "";
        document.getElementById("name").value = student.name ?? "";
        document.getElementById("registerNo").value =
            student.register_no ?? student.registerNo ?? "";
        document.getElementById("department").value = student.department ?? "";
        document.getElementById("email").value = student.email ?? "";
        document.getElementById("phone").value = student.phone ?? "";

        saveBtn.textContent = "Update Student";
        showStatus("Edit the details and click Update Student.");
        form.scrollIntoView({ behavior: "smooth", block: "start" });
    }

    // Delete a student
    async function deleteStudent(id) {
        if (!confirm("Are you sure you want to delete this student?")) {
            return;
        }

        try {
            await request(`${API_URL}?id=${encodeURIComponent(id)}`, {
                method: "DELETE",
                headers: { Accept: "application/json" }
            });

            showStatus("Student deleted successfully.");
            await loadStudents();
        } catch (error) {
            console.error("Delete student failed:", error);
            showStatus(error.message, true);
        }
    }

    // Clear form
    clearBtn.addEventListener("click", () => {
        form.reset();
        studentId.value = "";
        saveBtn.textContent = "Register Student";
        showStatus("Form cleared.");
    });

    loadStudents();
});