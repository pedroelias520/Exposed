package org.jetbrains.exposed.sql.tests.shared.ddl

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.tests.inProperCase
import org.jetbrains.exposed.sql.tests.shared.assertEqualCollections
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.SQLiteDialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.junit.Test
import java.util.*
import kotlin.test.assertFails

class CreateTableTests : DatabaseTestsBase() {
    // https://github.com/JetBrains/Exposed/issues/709
    @Test
    fun createTableWithDuplicateColumn() {
        val assertionFailureMessage = "Can't create a table with multiple columns having the same name"

        withDb {
            assertFails(assertionFailureMessage) {
                SchemaUtils.create(TableWithDuplicatedColumn)
            }
            assertFails(assertionFailureMessage) {
                SchemaUtils.create(TableDuplicatedColumnRefereToIntIdTable)
            }
            assertFails(assertionFailureMessage) {
                SchemaUtils.create(TableDuplicatedColumnRefereToTable)
            }
        }
    }

    @Test
    fun primaryKeyCreateTableTest() {
        val account = object : Table("Account") {
            val id1 = integer("id1")
            val id2 = integer("id2")

            override val primaryKey = PrimaryKey(id1, id2)
        }
        withDb {
            val id1ProperName = account.id1.name.inProperCase()
            val id2ProperName = account.id2.name.inProperCase()
            val tableName = account.tableName

            assertEquals(
                "CREATE TABLE " + addIfNotExistsIfSupported() + "${tableName.inProperCase()} (" +
                    "${account.columns.joinToString { it.descriptionDdl(false) }}, " +
                    "CONSTRAINT pk_$tableName PRIMARY KEY ($id1ProperName, $id2ProperName)" +
                    ")",
                account.ddl
            )
        }
    }

    @Test
    fun primaryKeyWithConstraintNameCreateTableTest() {
        val pkConstraintName = "PKConstraintName"

        // Table with composite primary key
        withDb {
            val id1ProperName = Person.id1.name.inProperCase()
            val id2ProperName = Person.id2.name.inProperCase()
            val tableName = Person.tableName

            assertEquals(
                "CREATE TABLE " + addIfNotExistsIfSupported() + "${tableName.inProperCase()} (" +
                    "${Person.columns.joinToString { it.descriptionDdl(false) }}, " +
                    "CONSTRAINT $pkConstraintName PRIMARY KEY ($id1ProperName, $id2ProperName)" +
                    ")",
                Person.ddl
            )
        }

        // Table with single column in primary key.
        val user = object : Table("User") {
            val user_name = varchar("user_name", 25)

            override val primaryKey = PrimaryKey(user_name, name = pkConstraintName)
        }
        withDb {
            val userNameProperName = user.user_name.name.inProperCase()
            val tableName = TransactionManager.current().identity(user)

            // Must generate primary key constraint, because the constraint name was defined.
            assertEquals(
                "CREATE TABLE " + addIfNotExistsIfSupported() + "$tableName (" +
                    "${user.columns.joinToString { it.descriptionDdl(false) }}, " +
                    "CONSTRAINT $pkConstraintName PRIMARY KEY ($userNameProperName)" +
                    ")",
                user.ddl
            )
        }
    }

    @Test
    fun addCompositePrimaryKeyToTableH2Test() {
        withDb(TestDB.H2) {
            val tableName = Person.tableName
            val tableProperName = tableName.inProperCase()
            val id1ProperName = Person.id1.name.inProperCase()
            val ddlId1 = Person.id1.ddl
            val id2ProperName = Person.id2.name.inProperCase()
            val ddlId2 = Person.id2.ddl
            val pkConstraintName = Person.primaryKey.name

            assertEquals(1, ddlId1.size)
            assertEquals("ALTER TABLE $tableProperName ADD ${Person.id1.descriptionDdl(false)}", ddlId1.first())

            assertEquals(2, ddlId2.size)
            assertEquals("ALTER TABLE $tableProperName ADD $id2ProperName ${Person.id2.columnType.sqlType()}", ddlId2.first())
            assertEquals("ALTER TABLE $tableProperName ADD CONSTRAINT $pkConstraintName PRIMARY KEY ($id1ProperName, $id2ProperName)", Person.id2.ddl.last())
        }
    }

    @Test
    fun addCompositePrimaryKeyToTableNotH2Test() {
        withTables(excludeSettings = listOf(TestDB.H2, TestDB.H2_MYSQL), tables = arrayOf(Person)) {
            val tableName = Person.tableName
            val tableProperName = tableName.inProperCase()
            val id1ProperName = Person.id1.name.inProperCase()
            val ddlId1 = Person.id1.ddl
            val id2ProperName = Person.id2.name.inProperCase()
            val ddlId2 = Person.id2.ddl
            val pkConstraintName = Person.primaryKey.name

            assertEquals(1, ddlId1.size)
            assertEquals("ALTER TABLE $tableProperName ADD ${Person.id1.descriptionDdl(false)}", ddlId1.first())

            assertEquals(1, ddlId2.size)
            assertEquals("ALTER TABLE $tableProperName ADD ${Person.id2.descriptionDdl(false)}, ADD CONSTRAINT $pkConstraintName PRIMARY KEY ($id1ProperName, $id2ProperName)", ddlId2.first())
        }
    }

    @Test
    fun addOneColumnPrimaryKeyToTableNotH2Test() {
        withTables(excludeSettings = listOf(TestDB.H2, TestDB.H2_MYSQL), tables = arrayOf(Book)) {
            val tableProperName = Book.tableName.inProperCase()
            val pkConstraintName = Book.primaryKey.name
            val id1ProperName = Book.id.name.inProperCase()
            val ddlId1 = Book.id.ddl

            if (currentDialectTest !is SQLiteDialect) {
                assertEquals(
                    "ALTER TABLE $tableProperName ADD ${Book.id.descriptionDdl(false)}, ADD CONSTRAINT $pkConstraintName PRIMARY KEY ($id1ProperName)",
                    ddlId1.first()
                )
            } else {
                assertEquals("ALTER TABLE $tableProperName ADD ${Book.id.descriptionDdl(false)}", ddlId1.first())
            }
        }
    }

    object Book : Table("Book") {
        val id = integer("id").autoIncrement()

        override val primaryKey = PrimaryKey(id, name = "PKConstraintName")
    }

    object Person : Table("Person") {
        val id1 = integer("id1")
        val id2 = integer("id2")

        override val primaryKey = PrimaryKey(id1, id2, name = "PKConstraintName")
    }

    object TableWithDuplicatedColumn : Table("myTable") {
        val id1 = integer("id")
        val id2 = integer("id")
    }

    object IDTable : IntIdTable("IntIdTable")

    object TableDuplicatedColumnRefereToIntIdTable : IntIdTable("myTable") {
        val reference = reference("id", IDTable)
    }

    object TableDuplicatedColumnRefereToTable : Table("myTable") {
        val reference = reference("id", TableWithDuplicatedColumn.id1)
    }

    @Test
    fun createTableWithExplicitForeignKeyName1() {
        val fkName = "MyForeignKey1"
        val parent = object : LongIdTable("parent1") {}
        val child = object : LongIdTable("child1") {
            val parentId = reference(
                name = "parent_id",
                foreign = parent,
                onUpdate = ReferenceOption.NO_ACTION,
                onDelete = ReferenceOption.NO_ACTION,
                fkName = fkName
            )
        }
        withTables(parent, child) {
            val t = TransactionManager.current()
            val expected = listOfNotNull(
                child.autoIncColumn?.autoIncColumnType?.autoincSeq?.let { Sequence(it).createStatement().single() },
                "CREATE TABLE " + addIfNotExistsIfSupported() + "${t.identity(child)} (" +
                    "${child.columns.joinToString { it.descriptionDdl(false) }}," +
                    " CONSTRAINT ${t.db.identifierManager.cutIfNecessaryAndQuote(fkName).inProperCase()}" +
                    " FOREIGN KEY (${t.identity(child.parentId)})" +
                    " REFERENCES ${t.identity(parent)}(${t.identity(parent.id)})" +
                    ")"
            )
            assertEqualCollections(expected, child.ddl)
        }
    }

    @Test
    fun createTableWithExplicitForeignKeyName2() {
        val fkName = "MyForeignKey2"
        val parent = object : LongIdTable("parent2") {
            val uniqueId = uuid("uniqueId").clientDefault { UUID.randomUUID() }.uniqueIndex()
        }
        val child = object : LongIdTable("child2") {
            val parentId = reference(
                name = "parent_id",
                refColumn = parent.uniqueId,
                onUpdate = ReferenceOption.NO_ACTION,
                onDelete = ReferenceOption.NO_ACTION,
                fkName = fkName
            )
        }
        withTables(parent, child) {
            val t = TransactionManager.current()
            val expected = listOfNotNull(
                child.autoIncColumn?.autoIncColumnType?.autoincSeq?.let { Sequence(it).createStatement().single() },
                "CREATE TABLE " + addIfNotExistsIfSupported() + "${t.identity(child)} (" +
                    "${child.columns.joinToString { it.descriptionDdl(false) }}," +
                    " CONSTRAINT ${t.db.identifierManager.cutIfNecessaryAndQuote(fkName).inProperCase()}" +
                    " FOREIGN KEY (${t.identity(child.parentId)})" +
                    " REFERENCES ${t.identity(parent)}(${t.identity(parent.uniqueId)})" +
                    ")"
            )
            assertEqualCollections(expected, child.ddl)
        }
    }

    @Test
    fun createTableWithExplicitForeignKeyName3() {
        val fkName = "MyForeignKey3"
        val parent = object : LongIdTable("parent3") {}
        val child = object : LongIdTable("child3") {
            val parentId = optReference(
                name = "parent_id",
                foreign = parent,
                onUpdate = ReferenceOption.NO_ACTION,
                onDelete = ReferenceOption.NO_ACTION,
                fkName = fkName
            )
        }
        withTables(parent, child) {
            val t = TransactionManager.current()
            val expected = listOfNotNull(
                child.autoIncColumn?.autoIncColumnType?.autoincSeq?.let { Sequence(it).createStatement().single() },
                "CREATE TABLE " + addIfNotExistsIfSupported() + "${t.identity(child)} (" +
                    "${child.columns.joinToString { it.descriptionDdl(false) }}," +
                    " CONSTRAINT ${t.db.identifierManager.cutIfNecessaryAndQuote(fkName).inProperCase()}" +
                    " FOREIGN KEY (${t.identity(child.parentId)})" +
                    " REFERENCES ${t.identity(parent)}(${t.identity(parent.id)})" +
                    ")"
            )
            assertEqualCollections(expected, child.ddl)
        }
    }

    @Test
    fun createTableWithExplicitForeignKeyName4() {
        val fkName = "MyForeignKey4"
        val parent = object : LongIdTable("parent4") {
            val uniqueId = uuid("uniqueId").clientDefault { UUID.randomUUID() }.uniqueIndex()
        }
        val child = object : LongIdTable("child4") {
            val parentId = optReference(
                name = "parent_id",
                refColumn = parent.uniqueId,
                onUpdate = ReferenceOption.NO_ACTION,
                onDelete = ReferenceOption.NO_ACTION,
                fkName = fkName
            )
        }
        withTables(parent, child) {
            val t = TransactionManager.current()
            val expected = listOfNotNull(
                child.autoIncColumn?.autoIncColumnType?.autoincSeq?.let { Sequence(it).createStatement().single() },
                "CREATE TABLE " + addIfNotExistsIfSupported() + "${t.identity(child)} (" +
                    "${child.columns.joinToString { it.descriptionDdl(false) }}," +
                    " CONSTRAINT ${t.db.identifierManager.cutIfNecessaryAndQuote(fkName).inProperCase()}" +
                    " FOREIGN KEY (${t.identity(child.parentId)})" +
                    " REFERENCES ${t.identity(parent)}(${t.identity(parent.uniqueId)})" +
                    ")"
            )
            assertEqualCollections(expected, child.ddl)
        }
    }

    @Test
    fun createTableWithExplicitCompositeForeignKeyName1() {
        val fkName = "MyForeignKey1"
        val parent = object : Table("parent1") {
            val idA = integer("id_a")
            val idB = integer("id_b")
            override val primaryKey = PrimaryKey(idA, idB)
        }
        val child = object : Table("child1") {
            val idA = integer("id_a")
            val idB = integer("id_b")

            init {
                foreignKey(
                    idA, idB,
                    target = parent.primaryKey,
                    onUpdate = ReferenceOption.CASCADE,
                    onDelete = ReferenceOption.CASCADE,
                    name = fkName
                )
            }
        }
        withTables(parent, child) {
            val t = TransactionManager.current()
            val expected = listOfNotNull(
                child.autoIncColumn?.autoIncColumnType?.autoincSeq?.let { Sequence(it).createStatement().single() },
                "CREATE TABLE " + addIfNotExistsIfSupported() + "${t.identity(child)} (" +
                    "${child.columns.joinToString { it.descriptionDdl(false) }}," +
                    " CONSTRAINT ${t.db.identifierManager.cutIfNecessaryAndQuote(fkName).inProperCase()}" +
                    " FOREIGN KEY (${t.identity(child.idA)}, ${t.identity(child.idB)})" +
                    " REFERENCES ${t.identity(parent)}(${t.identity(parent.idA)}, ${t.identity(parent.idB)})" +
                    " ON DELETE CASCADE ON UPDATE CASCADE)"
            )
            assertEqualCollections(expected, child.ddl)
        }
    }

    @Test
    fun createTableWithExplicitCompositeForeignKeyName2() {
        val fkName = "MyForeignKey2"
        val parent = object : Table("parent2") {
            val idA = integer("id_a")
            val idB = integer("id_b")
            init {
                uniqueIndex(idA, idB)
            }
        }
        val child = object : Table("child2") {
            val idA = integer("id_a")
            val idB = integer("id_b")

            init {
                foreignKey(
                    idA to parent.idA, idB to parent.idB,
                    onUpdate = ReferenceOption.NO_ACTION,
                    onDelete = ReferenceOption.NO_ACTION,
                    name = fkName
                )
            }
        }
        withTables(parent, child) {
            val t = TransactionManager.current()
            val expected = listOfNotNull(
                child.autoIncColumn?.autoIncColumnType?.autoincSeq?.let { Sequence(it).createStatement().single() },
                "CREATE TABLE " + addIfNotExistsIfSupported() + "${t.identity(child)} (" +
                    "${child.columns.joinToString { it.descriptionDdl(false) }}," +
                    " CONSTRAINT ${t.db.identifierManager.cutIfNecessaryAndQuote(fkName).inProperCase()}" +
                    " FOREIGN KEY (${t.identity(child.idA)}, ${t.identity(child.idB)})" +
                    " REFERENCES ${t.identity(parent)}(${t.identity(parent.idA)}, ${t.identity(parent.idB)})" +
                    ")"
            )
            assertEqualCollections(expected, child.ddl)
        }
    }

    object OneTable : IntIdTable("one")
    object OneOneTable : IntIdTable("one.one")

    @Test
    fun `test create table with same name in different schemas`() {
        val one = Schema("one")
        withDb(excludeSettings = listOf(TestDB.SQLITE)) { testDb ->
            assertEquals(false, OneTable.exists())
            assertEquals(false, OneOneTable.exists())
            try {
                SchemaUtils.create(OneTable)
                assertEquals(true, OneTable.exists())
                assertEquals(false, OneOneTable.exists())
                SchemaUtils.createSchema(one)
                SchemaUtils.create(OneOneTable)
                println("${currentDialect.name}: ${currentDialectTest.allTablesNames()}")
                assertEquals(true, OneTable.exists())
                assertEquals(true, OneOneTable.exists())
            } finally {
                SchemaUtils.drop(OneTable, OneOneTable)
                val cascade = testDb != TestDB.SQLSERVER
                SchemaUtils.dropSchema(one, cascade = cascade)
            }
        }
    }
}
