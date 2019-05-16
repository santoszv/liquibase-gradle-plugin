/*
 *    Copyright 2019 Santos Zatarain Vera (coder.santoszv_at_gmail.com)
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package mx.com.inftel.liquibase.gradle.plugin

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import kotlin.reflect.full.primaryConstructor

fun Project.liquibase(configuration: Action<LiquibaseExtension>) = configuration.execute(extensions.getByName("liquibase") as LiquibaseExtension)

fun DependencyHandler.liquibaseRuntime(dependencyNotation: Any): Dependency? = add("liquibaseRuntime", dependencyNotation)

class LiquibasePlugin : Plugin<Project> {

    override fun apply(target: Project) {
        target.configurations.maybeCreate("liquibaseRuntime")
        target.extensions.create("liquibase", LiquibaseExtension::class.java, target)
    }
}

open class LiquibaseExtension(private val project: Project) {

    fun db(name: String, block: LiquibaseParameters.() -> Unit) {
        val parameters = LiquibaseParameters()
        parameters.block()
        for (command in LiquibaseCommandEnum.values()) {
            project.tasks.register("$name${command.name}", LiquibaseTask::class.java) {
                it.group = "liquibase"
                it.description = command.description
                it.command = command
                it.parameters = parameters
            }
        }
    }
}

open class LiquibaseTask : DefaultTask() {

    @field:Internal
    lateinit var command: LiquibaseCommandEnum
    @field:Internal
    lateinit var parameters: LiquibaseParameters

    @TaskAction
    fun run() {
        val configuration = project.configurations.getByName("liquibaseRuntime")
        val originalClassLoader = Thread.currentThread().contextClassLoader
        val runtimeClassLoader = run {
            val urls = configuration.files.map {
                it.toURI().toURL()
            }
            LiquibaseClassLoader(urls.toTypedArray(), originalClassLoader)
        }
        try {
            Thread.currentThread().contextClassLoader = runtimeClassLoader
            val kClass = runtimeClassLoader.loadClass("mx.com.inftel.liquibase.gradle.plugin.LiquibaseRunner").kotlin
            val runnable = kClass.primaryConstructor!!.call(project, command, parameters) as Runnable
            runnable.run()
        } finally {
            Thread.currentThread().contextClassLoader = originalClassLoader
            runtimeClassLoader.close()
        }
    }
}

class LiquibaseParameters {

    var changeLogDirectory: String = "liquibase"
    var changeLogFile: String = "db.changelog.xml"

    var url: String? = null
    var username: String? = null
    var password: String? = null
    var driver: String? = null

    var contexts: String? = null
    var changesToApply: Int = 0
    var tagToRollBackTo: String? = null
    var changesToRollback: Int = 0

    var outputUpdateSQL: String = "build/liquibase/update.sql"
    var outputRollbackSQL: String = "build/liquibase/rollback.sql"
    var outputSyncSQL: String = "build/liquibase/sync.sql"

    var outputChangeLogFile: String = "build/liquibase/db.changelog.xml"
    var outputChangeSetAuthor: String? = null

    var outputDbDocDir: String = "build/liquibase/doc"

    var dataDirectory: String? = null

    var tag: String? = null

    var defaultCatalogName: String? = null
    var defaultSchemaName: String? = null
    var outputDefaultCatalog: Boolean = false
    var outputDefaultSchema: Boolean = false

    var databaseClass: String? = null
    var driverPropertiesFile: String? = null
    var propertyProviderClass: String? = null

    var liquibaseCatalogName: String? = null
    var liquibaseSchemaName: String? = null
    var databaseChangeLogTableName: String? = null
    var databaseChangeLogTableNameLockTable: String? = null
}

enum class LiquibaseCommandEnum(val description: String) {
    // Database Update Commands
    Update("Updates database to current version."),
    UpdateCount("Applies the next <value> change sets."),
    UpdateSQL("Writes SQL to update database to current version to a file."),
    UpdateCountSQL("Writes SQL to apply the next <value> change sets to a file."),
    // Database Rollback Commands
    Rollback("Rolls back the database to the state it was in when the tag was applied."),
    //RollbackToDate("Rolls back the database to the state it was in at the given date/time."),
    RollbackCount("Rolls back the last <value> change sets."),
    RollbackSQL("Writes SQL to roll back the database to the state it was in when the tag was applied to a file."),
    //RollbackToDateSQL("Writes SQL to roll back the database to the state it was in at the given date/time version to a file."),
    RollbackCountSQL("Writes SQL to roll back the last <value> change sets to a file."),
    //FutureRollbackSQL("Writes SQL to roll back the database to the current state after the changes in the changeslog have been applied."),
    //UpdateTestingRollback("Updates the database, then rolls back changes before updating again."),
    GenerateChangeLog("Generate changeLog of the database to standard out. v1.8 requires the dataDir parameter currently."),
    // Diff Commands
    //Diff("Writes description of differences to standard out."),
    //DiffChangeLog("Writes Change Log XML to update the base database to the target database to standard out."),
    // Documentation Commands
    Doc("Generates Javadoc-like documentation based on current database and change log."),
    // Maintenance Commands
    Tag("Tags the current database state for future rollback."),
    TagExists("Checks whether the given tag is already existing."),
    Status("Outputs count of unrun change sets."),
    Validate("Checks the changelog for errors."),
    ChangelogSync("Mark all changes as executed in the database."),
    ChangelogSyncSQL("Writes SQL to mark all changes as executed in the database to a file."),
    //MarkNextChangeSetRan("Mark the next change set as executed in the database."),
    ListLocks("Lists who currently has locks on the database changelog."),
    ReleaseLocks("Releases all locks on the database changelog."),
    DropAll("Drops all database objects owned by the user. Note that functions, procedures and packages are not dropped (limitation in 1.8.1)."),
    ClearCheckSums("Removes current checksums from database. On next run checksums will be recomputed.")
}