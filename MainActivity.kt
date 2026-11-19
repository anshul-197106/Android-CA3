package com.example.ca3

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Entity(tableName = "students")
data class Student(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String
)

@Entity(
    tableName = "subjects",
    foreignKeys = [
        ForeignKey(
            entity = Student::class,
            parentColumns = ["id"],
            childColumns = ["studentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("studentId")]
)
data class Subject(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val studentId: Int,
    val name: String
)

data class StudentWithSubjects(
    @Embedded val student: Student,
    @Relation(
        parentColumn = "id",
        entityColumn = "studentId"
    )
    val subjects: List<Subject>
)

@Dao
abstract class StudentDao {

    @Insert
    abstract suspend fun insertStudent(student: Student)

    @Insert
    abstract suspend fun insertSubject(subject: Subject)

    @Transaction
    @Query("SELECT * FROM students")
    abstract fun getStudents(): Flow<List<StudentWithSubjects>>

    @Query("SELECT COUNT(*) FROM subjects WHERE studentId = :studentId")
    abstract suspend fun getSubjectCount(studentId: Int): Int

    @Transaction
    open suspend fun addSubject(studentId: Int, name: String): Boolean {
        if (getSubjectCount(studentId) >= 3) {
            return false
        }

        insertSubject(Subject(studentId = studentId, name = name))
        return true
    }
}

@Database(
    entities = [Student::class, Subject::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun studentDao(): StudentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "student_database"
                ).build().also {
                    INSTANCE = it
                }
            }
        }
    }
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dao = AppDatabase.getDatabase(this).studentDao()

        setContent {
            MaterialTheme {
                StudentScreen(dao)
            }
        }
    }
}

@Composable
fun StudentScreen(dao: StudentDao) {

    val students by dao.getStudents().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var studentName by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Text(
            "Student Subject Manager",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = studentName,
            onValueChange = { studentName = it },
            label = { Text("Student Name") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                if (studentName.isNotBlank()) {
                    scope.launch {
                        dao.insertStudent(Student(name = studentName))
                        studentName = ""
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Add Student")
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn {
            items(students, key = { it.student.id }) {
                StudentItem(it, dao)
            }
        }
    }
}

@Composable
fun StudentItem(
    item: StudentWithSubjects,
    dao: StudentDao
) {

    val scope = rememberCoroutineScope()
    var subjectName by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {

        Column(modifier = Modifier.padding(12.dp)) {

            Text(
                item.student.name,
                style = MaterialTheme.typography.titleLarge
            )

            Text("Subjects: ${item.subjects.size}/3")

            item.subjects.forEach {
                Text("• ${it.name}")
            }

            OutlinedTextField(
                value = subjectName,
                onValueChange = { subjectName = it },
                label = { Text("Subject Name") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    if (subjectName.isNotBlank()) {
                        scope.launch {
                            val result = dao.addSubject(
                                item.student.id,
                                subjectName
                            )

                            message = if (result) {
                                subjectName = ""
                                "Subject Added"
                            } else {
                                "Maximum 3 subjects allowed!"
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add Subject")
            }

            Text(message)
        }
    }
}
