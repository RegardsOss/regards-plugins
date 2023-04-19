package fr.cnes.regards.modules.storage.plugin.s3;

import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;
import fr.cnes.regards.modules.storage.domain.plugin.*;

/**
 * Main class of plugin of storage(online type) in S3 server
 */
@Plugin(author = "REGARDS Team",
        description = "Plugin handling the storage on S3",
        id = "S3",
        version = "1.0",
        contact = "regards@c-s.fr",
        license = "GPLv3",
        owner = "CNES",
        markdown = "S3GlacierPlugin.md",
        url = "https://regardsoss.github.io/")
public class S3Glacier extends S3OnlineStorage implements INearlineStorageLocation {

    public static final String GLACIER_WORKSPACE_PATH = "Glacier_Workspace_Path";

    public static final String GLACIER_SMALL_FILE_MAX_SIZE = "Glacier_Small_File_Max_Size";

    public static final String GLACIER_SMALL_FILE_ARCHIVE_MAX_SIZE = "Glacier_Small_File_Archive_Max_Size";

    public static final String GLACIER_SMALL_FILE_ARCHIVE_DURATION_IN_HOURS = "Glacier_Small_File_Archive_Duration_In_Hours";

    public static final String GLACIER_PARALLEL_TASK_NUMBER = "Glacier_Parallel_Upload_Number";

    public static final String GLACIER_LOCAL_WORKSPACE_FILE_LIFETIME_IN_HOURS = "Glacier_Local_Workspace_File_Lifetime_In_Hours";

    public static final String GLACIER_S3_ACCESS_TRY_TIMEOUT = "Glacier_S3_Access_Try_Timeout";

    @PluginParameter(name = GLACIER_WORKSPACE_PATH,
                     description = "Local workspace for archive building and restoring cache",
                     label = "Workspace path")
    private String workspacePath;

    @PluginParameter(name = GLACIER_SMALL_FILE_MAX_SIZE,
                     description = "Maximum size of a file for it treated as a small file and stored in group",
                     label = "Small file max size",
                     defaultValue = "1048576")
    private int smallFileMaxSize;

    @PluginParameter(name = GLACIER_SMALL_FILE_ARCHIVE_MAX_SIZE,
                     description = "Determines when the small files archive is considered full and should be closed",
                     label = "Archive max size",
                     defaultValue = "10485760")
    private int archiveMaxSize;

    @PluginParameter(name = GLACIER_SMALL_FILE_ARCHIVE_DURATION_IN_HOURS,
                     description = "Determines when the small files archive is considered too old and should be closed, in hours",
                     label = "Archive max age",
                     defaultValue = "24")
    private int archiveMaxAge;

    @PluginParameter(name = GLACIER_PARALLEL_TASK_NUMBER,
                     description = "Number of tasks that can be handled at the same time in each job. The "
                                   + "different tasks (Store, Restore, Delete) use the same thread pool ",
                     label = "Parallel Task Number",
                     defaultValue = "20")
    private int parallelTaskNumber;

    @PluginParameter(name = GLACIER_LOCAL_WORKSPACE_FILE_LIFETIME_IN_HOURS,
                     description = "Duration for which the restored files will be kept in the local workspace",
                     label = "Local workspace duration",
                     defaultValue = "24")
    private int localWorkspaceFileLifetime;

    @PluginParameter(name = GLACIER_S3_ACCESS_TRY_TIMEOUT,
                     description = "Timeout during S3 access after which the job will return an error",
                     label = "S3 Access Timeout",
                     defaultValue = "3600")
    private int s3AccessTimeout;

    @Override
    public void store(FileStorageWorkingSubset workingSet, IStorageProgressManager progressManager) {
        //TODO
    }

    @Override
    public void retrieve(FileRestorationWorkingSubset workingSubset, IRestorationProgressManager progressManager) {
        //TODO
    }

    @Override
    public void delete(FileDeletionWorkingSubset workingSet, IDeletionProgressManager progressManager) {
        //TODO
    }

    @Override
    public void runPeriodicAction(IPeriodicActionProgressManager progressManager) {
        submitReadyArchives(progressManager);
        cleanLocalWorkspace(progressManager);
    }

    private void submitReadyArchives(IPeriodicActionProgressManager progressManager) {
        //TODO
    }

    private void cleanLocalWorkspace(IPeriodicActionProgressManager progressManager) {
        //TODO
    }
}
