S3 Glacier Plugin
========================

This plugin allows managing files on a S3 Glacier (Tier 3) server

This plugin allows to :

- Store files on the glacier
- Restore files by asking the glacier to copy files from the tier 3 to the tier 2 server
- Delete files, either only virtually or both virtually and physically on the serve

### Progress manager

Each plugin method send a response through the progress manager once the process is finished.
The response can either be a success or a failure. The response will always contain the request, and in case of error
will also contain the cause of the failure.

For the storage method, the response will also contain the URL of the file on the server. It will also contain a
flag indicating if there is still an on-going process on the stored file. The additional process will be explained
in the next section.

### Small files handling

This plugin handles files differently according to their size.
The **size threshold** is one of the plugin parameters.
A file which size is under the threshold will be called a **small file**.
The path of the file on the storage will be called the **node**.

The small files sharing the same node will be stored together in a zip archive
in order to reduce the number of server accesses required when storing or retrieving
multiple small files.

This means that when a small file request is processed, the file won't be immediately
stored on the glacier. The file will be stored locally, and it will be possible to retrieve it.
The plugin will inform in the storage response that the file is still being processed (but is available nonetheless).

Once the archive is sent to the storage, the plugin will inform that the processing of the files in the archive is
completed.

### Cache

The plugin caches the restored file in order to prevent the same file to be downloaded
many times from the server. Notably, this means that the archive containing multiple small
requested files will be downloaded only once when requesting multiple files with the same node.

### Periodic actions

A set of actions to run periodically is defined, those actions only run when no other process is running.

Those actions are :

- Close the directories containing small files even if there aren't full if they are older than the directory
  age threshold
- Create the archives containing the small files
- Send the archives to the glacier
- Inform that the pending actions for the small files that were just sent are finished
- Delete the archives and the directories that were sent
- Clean the cache

### Plugin parameters

| Parameter Name                                        | Description                                                                                                                                                 | Default value<br/>(mandatory if empty) |
|-------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------|
| **S3_Server_Endpoint**                                | Http address of the S3 Glacier server                                                                                                                       |                                        |
| **S3_Server_Region**                                  | Geographical region configured in the S3                                                                                                                    |                                        |
| **S3_Server_Key**                                     | Login of the S3                                                                                                                                             |                                        |
| **S3_Server_Secret**                                  | Password of the S3                                                                                                                                          |                                        |
| **S3_Server_Bucket**                                  | S3 Bucket configured to store all the files by this plugin                                                                                                  |                                        |
| **Root_Path**                                         | Root path in the bucket of all the files stored by this plugin                                                                                              | *empty*                                |
| **Upload_With_Multipart_Threshold_In_Mb**             | Maximum size of file in Mb for single part upload, if the size is larger than the threshold multipart upload will be used                                   | 5                                      |
| **Upload_With_Multipart_Parallel_Part_Number**        | Number of parts to split the file into for multipart upload                                                                                                 | 5                                      |
| **S3_Allow_Deletion**                                 | Allow deletion of files in the glacier, if false, the files will never be deleted in the glacier (but they will still be considered deleted in the storage) | false                                  |
| **Small_File_Workspace_Path**                         | Path of the workspace of the plugin in which cache and small files will be temporarily stored                                                               |                                        |
| **Small_File_Max_Size**                               | Max size threshold for a file to be considered small                                                                                                        | 1048576                                |
| **Small_File_Archive_Max_Size**                       | Max size of archives, when an archive size overpass this threshold, a   new one will be created to store the new files                                      | 10485760                               |
| **Small_File_Archive_Duration_In_Hours**              | Time in hours after which an archive will be closed even if its isn't full                                                                                  | 24                                     |
| **Small_File_Parallel_Upload_Number**                 | Number of different threads that can initiate S3 upload in parallel                                                                                         | 5                                      |
| **Small_File_Parallel_Restore_Number**                | Number of different threads that can initiate S3 download in parallel                                                                                       | 20                                     |
| **Small_File_Local_Workspace_File_Lifetime_In_Hours** | Duration of the cache                                                                                                                                       | 24                                     |
| **Glacier_S3_Access_Try_Timeout**                     | Time waited after a restoration request has been sent to the glacier, if after this time the file is still not available (Tier 2), the request fail.        | 3600                                   |



