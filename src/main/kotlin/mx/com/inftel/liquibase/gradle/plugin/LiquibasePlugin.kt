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
        target.dependencies.add("liquibaseRuntime", "${LiquibaseVersion.group}:${LiquibaseVersion.name}:${LiquibaseVersion.version}")
    }
}

open class LiquibaseExtension(private val project: Project) {

    fun database(name: String, block: LiquibaseParameters.() -> Unit) {
        val parameters = LiquibaseParameters()
        parameters.block()
        for (command in LiquibaseCommandEnum.values()) {
            project.tasks.register("$name${command.name}", LiquibaseTask::class.java) {
                it.group = "$name database"
                it.description = command.description(parameters)
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

enum class LiquibaseCommandEnum {
    // Database Update Commands
    DatabaseUpdate {
        override fun description(params: LiquibaseParameters) = "Updates database to current version."
    },
    DatabaseUpdateCount {
        override fun description(params: LiquibaseParameters) = "Applies the next ${params.changesToApply} change sets."
    },
    DatabaseUpdateSQL {
        override fun description(params: LiquibaseParameters) = "Writes SQL to update database to current version to a file."
    },
    DatabaseUpdateCountSQL {
        override fun description(params: LiquibaseParameters) = "Writes SQL to apply the next ${params.changesToApply} change sets to a file."
    },
    // Database Rollback Commands
    DatabaseRollback {
        override fun description(params: LiquibaseParameters) = "Rolls back the database to the state it was in when the tag '${params.tagToRollBackTo}' was applied."
    },
    //RollbackToDate("Rolls back the database to the state it was in at the given date/time."),
    DatabaseRollbackCount {
        override fun description(params: LiquibaseParameters) = "Rolls back the last ${params.changesToRollback} change sets."
    },
    DatabaseRollbackSQL {
        override fun description(params: LiquibaseParameters) = "Writes SQL to roll back the database to the state it was in when the tag '${params.tagToRollBackTo}' was applied to a file."
    },
    //RollbackToDateSQL("Writes SQL to roll back the database to the state it was in at the given date/time version to a file."),
    DatabaseRollbackCountSQL {
        override fun description(params: LiquibaseParameters) = "Writes SQL to roll back the last ${params.changesToRollback} change sets to a file."
    },
    //FutureRollbackSQL("Writes SQL to roll back the database to the current state after the changes in the changeslog have been applied."),
    //UpdateTestingRollback("Updates the database, then rolls back changes before updating again."),
    DatabaseGenerateChangeLog {
        override fun description(params: LiquibaseParameters) = "Generate changeLog of the database to standard out."
    },
    // Diff Commands
    //Diff("Writes description of differences to standard out."),
    //DiffChangeLog("Writes Change Log XML to update the base database to the target database to standard out."),
    // Documentation Commands
    DatabaseDoc {
        override fun description(params: LiquibaseParameters) = "Generates Javadoc-like documentation based on current database and change log."
    },
    // Maintenance Commands
    DatabaseTag {
        override fun description(params: LiquibaseParameters) = "Tags with '${params.tag}' the current database state for future rollback."
    },
    DatabaseTagExists {
        override fun description(params: LiquibaseParameters) = "Checks whether the given tag '${params.tag}' is already existing."
    },
    DatabaseStatus {
        override fun description(params: LiquibaseParameters) = "Outputs count of unrun change sets."
    },
    DatabaseValidate {
        override fun description(params: LiquibaseParameters) = "Checks the changelog for errors."
    },
    DatabaseChangelogSync {
        override fun description(params: LiquibaseParameters) = "Mark all changes as executed in the database."
    },
    DatabaseChangelogSyncSQL {
        override fun description(params: LiquibaseParameters) = "Writes SQL to mark all changes as executed in the database to a file."
    },
    //MarkNextChangeSetRan("Mark the next change set as executed in the database."),
    DatabaseListLocks {
        override fun description(params: LiquibaseParameters) = "Lists who currently has locks on the database changelog."
    },
    DatabaseReleaseLocks {
        override fun description(params: LiquibaseParameters) = "Releases all locks on the database changelog."
    },
    DatabaseDropAll {
        override fun description(params: LiquibaseParameters) = "Drops all database objects owned by the user."
    },
    DatabaseClearCheckSums {
        override fun description(params: LiquibaseParameters) = "Removes current checksums from database. On next run checksums will be recomputed."
    };

    abstract fun description(params: LiquibaseParameters): String
}