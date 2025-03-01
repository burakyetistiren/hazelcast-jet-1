---
title: Sources and Sinks
description: Birds-eye view of all pre-defined sources available in Jet.
id: version-4.5.4-sources-sinks
original_id: sources-sinks
---

Hazelcast Jet comes out of the box with many different sources and sinks
that you can work with, these are also referred to as _connectors_.

## Unified File Connector API

> This section describes the Unified File Connector API introduced in
> Hazelcast Jet 4.4.
>
> As of version 4.4, the API provides source capability only.
> For sinks, see the [Files](#files) section.

The Unified File Connector API provides a simple way to read files,
unified across different sources of the data. Using API this you can
read files from the local filesystem, HDFS and cloud storage systems
such as Amazon S3, Google Cloud Storage or Azure Blob Storage. At the
same time the connector supports various formats of the data - text
files, CSV, Json, Avro, etc., regardless of the source.

### The Source

Hazelcast Jet supports the following sources:

* Local Filesystem (both shared and local to the member)
* Hadoop Distributed File System (HDFS)
* Amazon S3
* Google Cloud Storage
* Azure Cloud Storage
* Azure Data Lake (both generation 1 and generation 2)

These are the officially supported sources. However, you can read from
any Hadoop compatible file system.

Support for reading from the local filesystem is included in the base
distribution of Hazelcast Jet. You don't need any additional
dependencies. To access Hadoop or any of the cloud based stores use the
separately downloadable module. See the details in the
[Supported Storage Systems](#supported-storage-systems) section.

The main entrypoint to the file connector is `FileSources.files`, which
takes a `path` as a String parameter and returns a `FileSourceBuilder`.
The following shows the simplest use of the file source, which reads a
text file line by line:

```java
BatchSource<String> source = FileSources.files("/path/to/my/directory")
                                        .build();
```

The `path` parameter takes an absolute path. It must point to a single
directory, it must not contain any wildcard characters. The directory is
not read recursively. The files in the directory can be filtered by
specifying a glob parameter - a pattern with wildcard characters (`*`,
`?`). For example, if a folder contains log files, named using
`YYYY-MM-DD.log` pattern, you can read all the files from January 2020
by setting the following parameters:

```java
BatchSource<String> source = FileSources.files("/var/log/")
                                        .glob("2020-01-*.log")
                                        .build();
```

You can also use Hadoop based connector module to read files from a
local filesystem. This might be beneficial when you need to parallelize
reading from a single large file, or read only subset of columns when
using Parquet format. You need to provide Hadoop module
(`hazelcast-jet-hadoop`) on classpath and create the source in the
following way:

```java
BatchSource<String> source = FileSources.files("/data")
                                        .glob("wikipedia.txt")
                                        .useHadoopForLocalFiles(true)
                                        .build();
```

You can provide additional options to Hadoop via `option(String,
String)` method. E.g. to read all files in a directory recursively:

```java
BatchSource<String> source = FileSources.files("/data")
                                        .glob("wikipedia.txt")
                                        .useHadoopForLocalFiles(true)
                                        .option("mapreduce.input.fileinputformat.input.dir.recursive", "true")
                                        .build();
```

### The Format

The `FileSourceBuilder` defaults to UTF-8 encoded text with the file
read line by line. You can specify the file format using
`format(FileFormat)` method.  See the available formats in
`FileFormat.*` interface.  E.g., create the source in the following way
to read the whole file as a single String using `FileFormat.text()`:

```java
BatchSource<String> source = FileSources.files("/path/to/my/directory")
                                        .format(FileFormat.text())
                                        .build();
```

#### Avro

Avro format allows to read data from _Avro Object Container File_
format. To use the Avro format you additionally need the
`hazelcast-jet-avro` module, located in the fat distribution in the
`lib` folder, or available as a dependency:

<!--DOCUSAURUS_CODE_TABS-->

<!--Gradle-->

```groovy
compile 'com.hazelcast.jet:hazelcast-jet-avro:4.5.4'
```

<!--Maven-->

```xml
<dependency>
  <groupId>com.hazelcast.jet</groupId>
  <artifactId>hazelcast-jet-avro</artifactId>
  <version>4.5.4</version>
</dependency>
```

<!--END_DOCUSAURUS_CODE_TABS-->

Suppose you have a class `User`, generated from the Avro schema, you can
read the data from an Avro file in the following way, notice that we
don't need to provide the User class to the builder, but we need to
satisfy the Java type system:

```java
BatchSource<User> source = FileSources.files("/data")
                                      .glob("users.avro")
                                      .format(FileFormat.<User>avro())
                                      .build();
```

This will use Avro's `SpecificDatumReader` under the hood.

If you don't have a class generated from the Avro schema, but the
structure of your class matches the data you can use Java reflection to
read the data:

```java
BatchSource<User> source = FileSources.files("/data")
                                      .glob("users.avro")
                                      .format(FileFormat.avro(User.class))
                                      .build();
```

This will use Avro's `ReflectDatumReader` under the hood.

#### CSV

The CSV files with a header are supported. The header columns must match
the class fields you want to deserialize into, columns not matching any
fields are ignored, fields not having corresponding columns have null
values.

Create the file source in the following way to read from file
`users.csv` and deserialize into a `User` class.

```java
BatchSource<User> source = FileSources.files("/data")
                                      .glob("users.csv")
                                      .format(FileFormat.csv(User.class))
                                      .build();
```

#### JSON

[JSON Lines](https://jsonlines.org/) files are supported. The JSON
fields must match the class fields you want to deserialize into.

Create the file source in the following way to read from file
`users.jsonl` and deserialize into a `User` class.

```java
BatchSource<User> source = FileSources.files("/data")
                                      .glob("users.jsonl")
                                      .format(FileFormat.json(User.class))
                                      .build();
```

#### Text

Create the file source in the following way to read file as text, whole
file is read as a single String:

```java
BatchSource<String> source = FileSources.files("/data")
                                        .glob("file.txt")
                                        .format(FileFormat.text())
                                        .build();
```

When reading from local filesystem you can specify the character
encoding. This is not supported when using the Hadoop based modules. If provided
the option will be ignored.

```java
BatchSource<String> source = FileSources.files("/data")
                                        .glob("file.txt")
                                        .format(FileFormat.text(Charset.forName("Cp1250")));
```

You can read file line by line in the following way, this is the default
and you can omit the `.format(FileFormat.lines())` part.

```java
BatchSource<String> source = FileSources.files("/data")
                                        .glob("file.txt")
                                        .format(FileFormat.lines())
                                        .build();
```

#### Parquet

Apache Parquet is a columnar storage format. It describes how the data
is stored on disk. It doesn't specify how the data is supposed to be
deserialized, and it uses other libraries to achieve that. Namely we
Apache Avro for deserialization.

Parquet has a dependency on Hadoop, so it can be used only with one of
the Hadoop based modules. You can still read parquet file from local
filesystem with the `.useHadoopForLocalFiles(true)` flag.

Create the file source in the following way to read data from a parquet
file:

```java
BatchSource<String> source = FileSources.files("/data")
                                        .glob("users.parquet")
                                        .format(FileFormat.<SpecificUser>parquet())
                                        .build();
```

#### Raw Binary

You can read binary files (e.g. images) in the following way:

```java
BatchSource<byte[]> source = FileSources.files("/data")
                                        .glob("file.txt")
                                        .format(FileFormat.bytes())
                                        .build();
```

### Supported Storage Systems

|Storage System|Module|Example path|
|:------------|:------------------|:--------------|
|HDFS|`hazelcast-jet-hadoop-all`|`hdfs://path/to/a/directory`|
|Amazon S3|`hazelcast-jet-files-s3`|`s3a://example-bucket/path/in/the/bucket`|
|Google Cloud Storage|`hazelcast-jet-files-gcs`|`gs://example-bucket/path/in/the/bucket`|
|Windows Azure Blob Storage|`hazelcast-jet-files-azure`|`wasbs://example-container@examplestorageaccount.blob.core.windows.net/path/in/the/container`|
|Azure Data Lake Generation 1|`hazelcast-jet-files-azure`|`adl://exampledatalake.azuredatalakestore.net/path/in/the/container`|
|Azure Data Lake Generation 2|`hazelcast-jet-files-azure`|`abfs://example-container@exampledatalakeaccount.dfs.core.windows.net/path/in/the/container`|

You can obtain the artifacts in the _Additional Modules_ section on the
[download page](/download) or download from Maven Central repository,
for example:

<!--DOCUSAURUS_CODE_TABS-->

<!--Gradle-->

```groovy
compile 'com.hazelcast.jet:hazelcast-jet-hadoop-all:4.5.4'
```

<!--Maven-->

```xml
<dependency>
  <groupId>com.hazelcast.jet</groupId>
  <artifactId>hazelcast-jet-hadoop-all</artifactId>
  <version>4.5.4</version>
  <classifier>jar-with-dependencies</classifier>
</dependency>
```

<!--END_DOCUSAURUS_CODE_TABS-->

#### Authentication

The basic authentication mechanisms are covered here. For additional
ways to authenticate see the linked documentation for the services.

Due to performance, the authentication is cached. This may cause issues
when submitting multiple jobs with different credentials, or even the
same jobs with new credentials, e.g. after credentials rotation.

You can turn off authentication caching by setting
`fs.<prefix>.impl.disable.cache` option to `true`. For the list of
prefixes see the table above.

#### Amazon S3

Provide your AWS access key id and secret key with required access via
`fs.s3a.access.key` and `fs.s3a.secret.key` options, using
`FileSourceBuilder#option` method on the source builder.

For additional ways to authenticate see the
[Hadoop-AWS documentation](https://hadoop.apache.org/docs/current/hadoop-aws/tools/hadoop-aws/index.html#Authenticating_with_S3)
and
[Amazon S3 documentation](https://docs.aws.amazon.com/sdk-for-java/v2/developer-guide/credentials.html)
.

#### Google Cloud Storage

Provide a location of the keyfile via
`google.cloud.auth.service.account.json.keyfile` source option, using
`FileSourceBuilder#option` method on the source builder. Note that
the file must be available on all the cluster members.

For additional ways to authenticate see
[Google Cloud Hadoop connector](https://github.com/GoogleCloudDataproc/hadoop-connectors/blob/master/gcs/CONFIGURATION.md#authentication).

#### Windows Azure Blob Storage

Provide an account key via
`fs.azure.account.key.<your account name>.blob.core.windows.net` source
option, using `FileSourceBuilder#option` method on the source
builder.

For additional ways to authenticate see
[Hadoop Azure Blob Storage](https://hadoop.apache.org/docs/stable/hadoop-azure/index.html)
support.

#### Azure Data Lake Generation 1

Provide the following properties using `FileSourceBuilder#option`
method on the source builder:

```text
fs.adl.oauth2.access.token.provider.type
fs.adl.oauth2.refresh.url
fs.adl.oauth2.client.id
fs.adl.oauth2.credential
```

For additional ways to authenticate see
[Hadoop Azure Data Lake Support](https://hadoop.apache.org/docs/stable/hadoop-azure-datalake/index.html)

#### Azure Data Lake Generation 2

For additional ways to authenticate see
[Hadoop Azure Data Lake Storage Gen2](https://hadoop.apache.org/docs/stable/hadoop-azure/abfs.html)

### Hadoop with Custom Classpath

Alternatively to using one of the modules with all the dependencies
included, you may use `hazelcast-jet-hadoop` module and configure the
classpath manually. The module is enabled by default (the
`hazelcast-jet-hadoop-4.5.4.jar` is in the `lib/` directory
).

Configure the classpath in the following way, using the
`hadoop classpath` command:

```bash
export CLASSPATH=$($HADOOP_HOME/bin/hadoop classpath)
```

Note that you must do these actions on both the node submitting the job
and all Jet cluster members.

### Hadoop Native Libraries

The underlying Hadoop infrastructure can make a use of native libraries
for compression/decompression and CRC checksums. When the native
libraries are not configured you will see the following message in logs:

```text
[o.a.h.u.NativeCodeLoader]: Unable to load native-hadoop library for your platform... using builtin-java classes where applicable
```

Configure the native libraries by adding the location to LD_LIBRARY_PATH
environment variable:

```bash
export LD_LIBRARY_PATH=<path to hadoop>/lib/native:$LD_LIBRARY_PATH
```

To verify that the Hadoop native libraries were successfully configured,
you should no longer see the message above and if you enable logging
for `org.apache.hadoop` you should see the following log message:

```text
[o.a.h.u.NativeCodeLoader]: Loaded the native-hadoop library
```

For more detail please see the [Hadoop
Native Libraries Guide](https://hadoop.apache.org/docs/stable/hadoop-project-dist/hadoop-common/NativeLibraries.html)
.

## Files

> This section describes our older API for file access. This API is
> still maintained, but all new development goes into the [Unified
> File Connector API](#unified-file-connector-api)

File sources generally involve reading a set of (as in "multiple") files
from either a local/network disk or a distributed file system such as
Amazon S3 or Hadoop. Most file sources and sinks are batch oriented, but
the sinks that support _rolling_ capability can also be used as sinks in
streaming jobs.

### Local Disk

The simplest file source is designed to work with both local and network
file systems. This source is text-oriented and reads the files line by
line and emits a record per line.

```java
Pipeline p = Pipeline.create();
p.readFrom(Sources.files("/home/data/web-logs"))
 .map(line -> LogParser.parse(line))
 .filter(log -> log.level().equals("ERROR"))
 .writeTo(Sinks.logger());
```

#### JSON Files

For JSON files, the source expects the content of the files as
[streaming JSON](https://en.wikipedia.org/wiki/JSON_streaming) content,
where each JSON string is separated by a new-line. The JSON string
itself can span on multiple lines. The source converts each JSON string
to an object of given type or to a `Map` if no type is specified:

```java
Pipeline p = Pipeline.create();
p.readFrom(Sources.json("/home/data/people", Person.class))
 .filter(person -> person.location().equals("NYC"))
 .writeTo(Sinks.logger());
```

Jet uses the lightweight JSON library `jackson-jr` to parse the given
input or to convert the given objects to JSON string. You can use
[Jackson Annotations](https://github.com/FasterXML/jackson-annotations/wiki/Jackson-Annotations)
by adding `jackson-annotations` library to the classpath, for example:

```java
public class Person {

    private long personId;
    private String name;

    @JsonGetter("id")
    public long getPersonId() {
      return this.personId;
    }

    @JsonSetter("id")
    public void setPersonId(long personId) {
      this.personId = personId;
    }

    public String getName() {
       return name;
    }

    public void setName(String name) {
      this.name = name;
    }
}
```

#### CSV

For CSV files or for parsing files in other custom formats it's possible
to use the `filesBuilder` source:

```java
Pipeline p = Pipeline.create();
p.readFrom(Sources.filesBuilder(sourceDir).glob("*.csv").build(path ->
    Files.lines(path).skip(1).map(SalesRecordLine::parse))
).writeTo(Sinks.logger());
```

#### Data Locality for Files

For a local file system, the sources expect to see on each node just the
files that node should read. You can achieve the effect of a distributed
source if you manually prepare a different set of files on each node.
For shared file system, the sources can split the work so that each node
will read a part of the files by configuring the option
`FilesBuilder.sharedFileSystem()`.

#### File Sink

The file sink, like the source works with text and creates a line of
output for each record. When the rolling option is used it will roll the
filename to a new one once the criteria is met. It supports rolling by
size or date. The following will roll to a new file every hour:

```java
Pipeline p = Pipeline.create();
p.readFrom(TestSources.itemStream(100))
 .withoutTimestamps()
 .writeTo(Sinks.filesBuilder("out")
 .rollByDate("YYYY-MM-dd.HH")
 .build());
```

To write JSON files, you can use `Sinks.json` or `Sinks.filesBuilder`
with `JsonUtil.toJson` as `toStringFn`. Sink converts each item to JSON
string and writes it as a new line to the file:

```java
Pipeline p = Pipeline.create();
p.readFrom(TestSources.itemStream(100))
 .withoutTimestamps()
 .writeTo(Sinks.json("out"));
```

Each node will write to a unique file with a numerical index. You can
achieve the effect of a distributed sink if you manually collect all the
output files on all members and combine their contents.

The sink also supports exactly-once processing and can work
transactionally.

#### File Watcher

File watcher is a streaming file source, where only the new files or
appended lines are emitted. If the files are modified in more complex
ways, the behavior is undefined.

```java
Pipeline p = Pipeline.create();
p.readFrom(Sources.fileWatcher("/home/data"))
 .withoutTimestamps()
 .writeTo(Sinks.logger());
```

You can create streaming file source for JSON files too:

```java
Pipeline p = Pipeline.create();
p.readFrom(Sources.jsonWatcher("/home/data", Person.class))
 .withoutTimestamps()
 .writeTo(Sinks.logger());
```

### Apache Avro

[Apache Avro](https://avro.apache.org/) is a binary data storage format
which is schema based. The connectors are similar to the local file
connectors, but work with binary files stored in _Avro Object Container
File_ format.

To use the Avro connector, make sure the `hazelcast-jet-avro`
module is present in the `lib` folder and add the following
dependency to your application:

<!--DOCUSAURUS_CODE_TABS-->

<!--Gradle-->

```groovy
compile 'com.hazelcast.jet:hazelcast-jet-avro:4.5.4'
```

<!--Maven-->

```xml
<dependency>
  <groupId>com.hazelcast.jet</groupId>
  <artifactId>hazelcast-jet-avro</artifactId>
  <version>4.5.4</version>
</dependency>
```

<!--END_DOCUSAURUS_CODE_TABS-->

With Avro sources, you can use either the `SpecificReader` or
`DatumReader` depending on the data type:

```java
Pipeline p = Pipeline.create();
p.readFrom(AvroSources.files("/home/data", Person.class))
 .filter(person -> person.age() > 30)
 .writeTo(Sinks.logger());
```

The sink expects a schema and the type to be written:

```java
p.writeTo(AvroSinks.files(DIRECTORY_NAME, Person.getClassSchema()), Person.class))
```

### Hadoop InputFormat/OutputFormat

You can use Hadoop connector to read/write files from/to Hadoop
Distributed File System (HDFS), local file system, or any other system
which has Hadoop connectors, including various cloud storages. Jet was
tested with:

* Amazon S3
* Google Cloud Storage
* Azure Cloud Storage
* Azure Data Lake

The Hadoop source and sink require a configuration object of type
[Configuration](https://hadoop.apache.org/docs/r2.10.0/api/org/apache/hadoop/conf/Configuration.html)
which supplies the input and output paths and formats. They don’t
actually create a MapReduce job, this config is simply used to describe
the required inputs and outputs. You can share the same `Configuration`
instance between several source/sink instances.

For example, to do a canonical word count on a Hadoop data source,
we can use the following pipeline:

```java
Job job = Job.getInstance();
job.setInputFormatClass(TextInputFormat.class);
job.setOutputFormatClass(TextOutputFormat.class);
TextInputFormat.addInputPath(job, new Path("input-path"));
TextOutputFormat.setOutputPath(job, new Path("output-path"));
Configuration configuration = job.getConfiguration();

Pipeline p = Pipeline.create();
p.readFrom(HadoopSources.inputFormat(configuration, (k, v) -> v.toString()))
 .flatMap(line -> traverseArray(line.toLowerCase().split("\\W+")))
 .groupingKey(word -> word)
 .aggregate(AggregateOperations.counting())
 .writeTo(HadoopSinks.outputFormat(configuration));
```

The Hadoop source and sink will use either the new or the old MapReduce
API based on the input format configuration.

Each processor will write to a different file in the output folder
identified by the unique processor id. The files will be in a temporary
state until the job is completed and will be committed when the job is
complete. For streaming jobs, they will be committed when the job is
cancelled. We have plans to introduce a rolling sink for Hadoop in the
future to have better streaming support.

#### Data Locality

Jet will split the input data across the cluster, with each processor
instance reading a part of the input. If the Jet nodes are co-located
with the Hadoop data nodes, then Jet can make use of data locality by
reading the blocks locally where possible. This can bring a significant
increase in read throughput.

#### Serialization and Writables

Hadoop types implement their own serialization mechanism through the use
of `Writable` types. Jet provides an adapter to register a `Writable`
for [Hazelcast serialization](serialization) without having to write
additional serialization code. To use this adapter, you can register
your own `Writable` types by extending `WritableSerializerHook` and
registering the hook.

#### Hadoop Classpath

To use the Hadoop connector, make sure the `hazelcast-jet-hadoop`
module is present in the `lib` folder and add the following
dependency to your application:

<!--DOCUSAURUS_CODE_TABS-->

<!--Gradle-->

```groovy
compile 'com.hazelcast.jet:hazelcast-jet-hadoop:4.5.4'
```

<!--Maven-->

```xml
<dependency>
  <groupId>com.hazelcast.jet</groupId>
  <artifactId>hazelcast-jet-hadoop</artifactId>
  <version>4.5.4</version>
</dependency>
```

<!--END_DOCUSAURUS_CODE_TABS-->

When submitting Jet jobs using Hadoop, sending Hadoop JARs should be
avoided and instead the Hadoop classpath should be used. Hadoop JARs
contain some JVM hooks and can keep lingering references inside the JVM
long after the job has ended, causing memory leaks.

To obtain the hadoop classpath, use the `hadoop classpath` command and
append the output to the `CLASSPATH` environment variable before
starting Jet.

### Amazon S3

The Amazon S3 connectors are text-based connectors that can read and
write files to Amazon S3 storage.

The connectors expect the user to provide either an `S3Client` instance
or credentials (or using the default ones) to create the client. The
source and sink assume the data is in the form of plain text and
emit/receive data items which represent individual lines of text.

```java
AwsBasicCredentials credentials = AwsBasicCredentials.create("accessKeyId", "accessKeySecret");
S3Client s3 = S3Client.builder()
    .credentialsProvider(StaticCredentialsProvider.create(credentials))
    .build();

Pipeline p = Pipeline.create();
p.readFrom(S3Sources.s3(singletonList("input-bucket"), "prefix",
    () -> S3Client.builder().credentialsProvider(StaticCredentialsProvider.create(credentials)).build())
 .filter(line -> line.contains("ERROR"))
 .writeTo(Sinks.logger());
```

The S3 sink works similar to the local file sink, writing a line to the
output for each input item:

```java
Pipeline p = Pipeline.create();
p.readFrom(TestSources.items("the", "brown", "fox"))
 .writeTo(S3Sinks.s3("output-bucket", () -> S3Client.create()));
```

The sink creates an object in the bucket for each processor instance.
Name of the file will include a user provided prefix (if defined),
followed by the processor’s global index. For example the processor
having the index `2` with prefix `my-object-` will create the object
`my-object-2`.

S3 sink uses the multi-part upload feature of S3 SDK. The sink buffers
the items to parts and uploads them after buffer reaches to the
threshold. The multi-part upload is completed when the job completes and
makes the objects available on the S3. Since a streaming jobs never
complete, S3 sink is not currently applicable to streaming jobs.

To use the S3 connector, you need to add the `hazelcast-jet-s3`
module to the `lib` folder and the following dependency to your
application:

<!--DOCUSAURUS_CODE_TABS-->

<!--Gradle-->

```groovy
compile 'com.hazelcast.jet:hazelcast-jet-s3:4.5.4'
```

<!--Maven-->

```xml
<dependency>
  <groupId>com.hazelcast.jet</groupId>
  <artifactId>hazelcast-jet-s3</artifactId>
  <version>4.5.4</version>
</dependency>
```

<!--END_DOCUSAURUS_CODE_TABS-->

## Messaging Systems

Messaging systems allow multiple application to communicate
asynchronously without a direct link between them. These types of
systems are a great fit for a stream processing engine like Jet since
Jet is able to consume messages from these systems and process them in
real time.

### Apache Kafka

Apache Kafka is a popular distributed, persistent log store which is a
great fit for stream processing systems. Data in Kafka is structured
as _topics_ and each topic consists of one or more partitions, stored in
the Kafka cluster.

To read from Kafka, the only requirements are to provide deserializers
and a topic name:

```java
Properties props = new Properties();
props.setProperty("bootstrap.servers", "localhost:9092");
props.setProperty("key.deserializer", StringDeserializer.class.getCanonicalName());
props.setProperty("value.deserializer", StringDeserializer.class.getCanonicalName());
props.setProperty("auto.offset.reset", "earliest");

Pipeline p = Pipeline.create();
p.readFrom(KafkaSources.kafka(props, "topic"))
 .withNativeTimestamps(0)
 .writeTo(Sinks.logger());
```

The topics and partitions are distributed across the Jet cluster, so
that each node is responsible for reading a subset of the data.

When used as a sink, then the only requirements are the serializers:

```java
Properties props = new Properties();
props.setProperty("bootstrap.servers", "localhost:9092");
props.setProperty("key.serializer", StringSerializer.class.getCanonicalName());
props.setProperty("value.serializer", StringSerializer.class.getCanonicalName());

Pipeline p = Pipeline.create();
p.readFrom(Sources.files("home/logs"))
 .map(line -> LogParser.parse(line))
 .map(log -> entry(log.service(), log.message()))
 .writeTo(KafkaSinks.kafka(props, "topic"));
```

To use the Kafka connector, make sure the `hazelcast-jet-kafka`
module is present in the `lib` folder and add the following
dependency to your application:

<!--DOCUSAURUS_CODE_TABS-->

<!--Gradle-->

```groovy
compile 'com.hazelcast.jet:hazelcast-jet-kafka:4.5.4'
```

<!--Maven-->

```xml
<dependency>
  <groupId>com.hazelcast.jet</groupId>
  <artifactId>hazelcast-jet-kafka</artifactId>
  <version>4.5.4</version>
</dependency>
```

<!--END_DOCUSAURUS_CODE_TABS-->

#### Fault Tolerance

One of the most important features of using Kafka as a source is that
it's possible to replay data - which enables fault-tolerance. If the job
has a processing guarantee configured, then Jet will periodically save
the current offsets internally and then replay from the saved offset
when the job is restarted. In this mode, Jet will manually track and
commit offsets, without interacting with the consumer groups feature of
Kafka.

If processing guarantee is disabled, the source will start reading from
default offsets (based on the `auto.offset.reset property`). You can
enable offset committing by assigning a `group.id`, enabling auto offset
committing using `enable.auto.commit` and configuring
`auto.commit.interval.ms` in the given properties. Refer to
[Kafka documentation](https://kafka.apache.org/22/documentation.html)
for the descriptions of these properties.

#### Transactional Guarantees

As a sink, it provides exactly-once guarantees at the cost of using
Kafka transactions: Jet commits the produced records after each snapshot
is completed. This greatly increases the latency because consumers see
the records only after they are committed.

If you use at-least-once guarantee, records are visible immediately, but
in the case of a failure some records could be duplicated. You
can also have the job in exactly-once mode and decrease the guarantee
just for a particular Kafka sink.

#### Schema Registry

Kafka is often used together with [Confluent Schema Registry](https://docs.confluent.io/current/schema-registry/index.html)
as a repository of types. The use of the schema registry is done through
adding it to the `Properties` object and using the `KafkaAvroSerializer/Deserializer`
if Avro is being used:

```java
properties.put("value.deserializer", KafkaAvroDeserializer.class);
properties.put("specific.avro.reader", true);
properties.put("schema.registry.url", schemaRegistryUrl);
```

Keep in mind that once the record deserialized, Jet still needs to know
how to serialize/deserialize the record internally. Please refer to the
[Serialization](serialization) section for details.

#### Version Compatibility

The Kafka sink and source are based on version 2.2.0, this means Kafka
connector will work with any client and broker having version equal to
or greater than 1.0.0.

### Amazon Kinesis

[Amazon Kinesis Data
Streams](https://aws.amazon.com/kinesis/data-streams/) (KDS) is a
massively scalable and durable real-time data streaming service. All
data items passing through it, called _records_, are assigned a
_partition key_. As the name suggests, partition keys group related
records together. Records with the same partition key are also ordered.
Partition keys are grouped into _shards_, the base throughput unit of
KDS. The input and output rates of shards is limited. Streams can be
resharded at any time.

To read from Kinesis, the only requirement is to provide a KDS stream
name. (Kinesis does not handle deserialization itself, it only provides
serialized binary data.)

```java
Pipeline p = Pipeline.create();
p.readFrom(KinesisSources.kinesis(STREAM).build())
  .withNativeTimestamps(0)
  .writeTo(Sinks.logger());
```

The shards are distributed across the Jet cluster, so that each node is
responsible for reading a subset of the partition keys.

When used as a sink, in order to be able to write out any type of data
items, the requirements are: KDS stream name, key function (specifies
how to compute the partition key from an input item), and the value
function (specifies how to compute the data blob from an input item -
the serialization).

```java
FunctionEx<Log, String> keyFn = l -> l.service();
FunctionEx<Log, byte[]> valueFn = l -> l.message().getBytes();
Sink<Log> sink = KinesisSinks.kinesis("stream", keyFn, valueFn).build();

p.readFrom(Sources.files("home/logs")) //read lines of text from log files
 .map(line -> LogParser.parse(line))   //parse lines into Log data objects
 .writeTo(sink);                       //write Log objects out to Kinesis
```

To use the Kinesis connectors, make sure the
`hazelcast-jet-kinesis` module is present in the `lib` folder
and add the following dependency to your application:

<!--DOCUSAURUS_CODE_TABS-->

<!--Gradle-->

```groovy
compile 'com.hazelcast.jet:hazelcast-jet-kinesis:4.5.4'
```

<!--Maven-->

```xml
<dependency>
  <groupId>com.hazelcast.jet</groupId>
  <artifactId>hazelcast-jet-kinesis</artifactId>
  <version>4.5.4</version>
</dependency>
```

<!--END_DOCUSAURUS_CODE_TABS-->

#### Fault-tolerance

Amazon Kinesis persists the data and it's possible to replay it (on a
per-shard basis). This enables fault tolerance. If a job has a
processing guarantee configured, then Jet will periodically save the
current shard offsets and then replay from the saved offsets when the
job is restarted. If no processing guarantee is enabled, the source will
start reading from the oldest available data, determined by the KDS
retention period (defaults to 24 hours, can be as long as 365 days).

While the source is suitable for both at-least-once and exactly-once
pipelines, the only processing guarantee the sink can support is
at-least-once. This is caused by the lack of transaction support in
Kinesis (can't write data into it with transactional guarantees) and the
AWS SDK occasionally causing data duplication on its own (see [Producer
Retries](https://docs.aws.amazon.com/streams/latest/dev/kinesis-record-processor-duplicates.html#kinesis-record-processor-duplicates-producer)
in the documentation).

#### Ordering

As stated before, Kinesis preserves the order of records with the same
partition key (or, more generally, the order of records belonging to the
same shard). However, neither the source nor the sink can fully uphold
this guarantee.

The problem scenario for the source is resharding. Resharding is the
process of adjusting the number of shards of a stream to adapt to data
flow rate changes. It is done voluntarily and explicitly by the stream's
owner, and it does not interrupt the flow of data through the stream.
During resharding, some (old) shards get closed, and new ones are
created - some partition keys transition from an old shard to a new one.
To keep the ordering for such a partition key in transit, Jet would need
to make sure that it finishes reading all the data from the old shard
before starting to read data from the new one. Jet would also need to
ensure that the new shard's data can't possibly overtake the old ones
data inside the Jet pipeline. Currently, Jet does not have a mechanism
to ensure this for such a distributed source. It's best to schedule
resharding when there are lulls in the data flow. Watermarks might also
manifest unexpected behaviour, if data is flowing during resharding.

The problem scenario for the sink is the ingestion data rate of a shard
being tripped. A KDS shard has an ingestion rate of 1MiB per second. If
you try to write more into it, then some records will be rejected. This
rejection breaks the ordering because the sinks write data in batches,
and the shards don't just reject entire batches, but random items from
them. What's rejected can (and is) retried, but the batch's original
ordering can't be preserved. The sink can't entirely avoid all
rejections because it's distributed, multiple instances of it write into
the same shard, and coordinating an aggregated rate among them is not
something currently possible in Jet and there can be also others sending
to the same stream. Truth be told, though, Kinesis also only preserves
the order of successfully ingested records, not the order in which
ingestion was attempted. Having enough shards and properly spreading out
partition keys should prevent the problem from happening.

### JMS

JMS (Java Message Service) is a standard API for communicating with
various message brokers using the queue or publish-subscribe patterns.

There are several brokers that implement the JMS standard, including:

* Apache ActiveMQ and ActiveMQ Artemis
* Amazon SQS
* IBM MQ
* RabbitMQ
* Solace
* ...

Jet is able to utilize these brokers both as a source and a sink through
the use of the JMS API.

To use a JMS broker, such as ActiveMQ, you need the client libraries
either on the classpath (by putting them into the `lib` folder) of the
node or submit them with the job. The Jet JMS connector is a part of the
`hazelcast-jet` module, so requires no other dependencies than the
client jar.

#### JMS Source Connector

A very simple pipeline which consumes messages from a given ActiveMQ
queue and then logs them is given below:

```java
Pipeline p = Pipeline.create();
p.readFrom(Sources.jmsQueue("queueName",
        () -> new ActiveMQConnectionFactory("tcp://localhost:61616")))
 .withoutTimestamps()
 .writeTo(Sinks.logger());
```

For a topic you can choose whether the consumer is durable or shared.
You need to use the `consumerFn` to create the desired consumer using a
JMS `Session` object.

If you create a shared consumer, you need to let Jet know by calling
`sharedConsumer(true)` on the builder. If you don't do this, only one
cluster member will actually connect to the JMS broker and will receive
all of the messages. We always assume a shared consumer for queues.

If you create a non-durable consumer, the fault-tolerance features won't
work since the JMS broker won't track which messages were delivered to
the client and which not.

Below is a simple example to create a non-durable non-shared topic
source:

```java
Pipeline p = Pipeline.create();
p.readFrom(Sources.jmsTopic("topic",
        () -> new ActiveMQConnectionFactory("tcp://localhost:61616")))
 .withoutTimestamps()
 .writeTo(Sinks.logger());
```

Here is a more complex example that uses a shared, durable consumer:

```java
Pipeline p = Pipeline.create();
p.readFrom(Sources
        .jmsTopicBuilder(() ->
                new ActiveMQConnectionFactory("tcp://localhost:61616"))
        .sharedConsumer(true)
        .consumerFn(session -> {
            Topic topic = session.createTopic("topic");
            return session.createSharedDurableConsumer(topic, "consumer-name");
        })
        .build())
 .withoutTimestamps()
 .writeTo(Sinks.logger());
```

#### Source Fault Tolerance

The source connector is fault-tolerant with the exactly-once guarantee
(except for the non-durable topic consumer). Fault tolerance is achieved
by acknowledging the consumed messages only after they were fully
processed by the downstream stages. Acknowledging is done once per
snapshot, you need to enable the processing guarantee in the
`JobConfig`.

In the exactly-once mode the processor saves the IDs of the messages
processed since the last snapshot into the snapshotted state. Therefore
this mode will not work if your messages don't have the JMS Message ID
set (it is an optional feature of JMS). In this case you need to set
`messageIdFn` on the builder to extract the message ID from the payload.
If you don't have a message ID to use, you must reduce the source
guarantee to at-least-once:

```java
p.readFrom(Sources.jmsTopicBuilder(...)
        .maxGuarantee(ProcessingGuarantee.AT_LEAST_ONCE)
        ...
```

In the at-least-once mode messages are acknowledged in the same way as
in the exactly-once mode, but message IDs are not saved to the snapshot.

If you have no processing guarantee enabled, the processor will consume
the messages in the `DUPS_OK_ACKNOWLEDGE` mode.

#### JMS Sink Connector

The JMS sink uses the supplied function to create a `Message` object for
each input item. The following code snippets show writing to a JMS queue
and a JMS topic using the ActiveMQ JMS client.

```java
Pipeline p = Pipeline.create();
p.readFrom(Sources.list("inputList"))
 .writeTo(Sinks.jmsQueue("queue",
         () -> new ActiveMQConnectionFactory("tcp://localhost:61616"))
 );
```

```java
Pipeline p = Pipeline.create();
p.readFrom(Sources.list("inputList"))
 .writeTo(Sinks.jmsTopic("topic",
        () -> new ActiveMQConnectionFactory("tcp://localhost:61616"))
 );
```

#### Fault Tolerance

The JMS sink supports the exactly-once guarantee. It uses two-phase XA
transactions, messages are committed consistent with the last state
snapshot. This greatly increases the latency, it is determined by the
snapshot interval: messages are visible to consumers only after the
commit. In order to make it work, the connection factory you provide has
to implement `javax.jms.XAConnectionFactory`, otherwise the job will not
start.

If you want to avoid the higher latency, decrease the overhead
introduced by the XA transactions, if your JMS implementation doesn't
support XA transactions or if you just don't need the guarantee, you can
reduce it just for the sink:

```java
stage.writeTo(Sinks
         .jmsQueueBuilder(() -> new ActiveMQConnectionFactory("tcp://localhost:61616"))
         // decrease the guarantee for the sink
         .exactlyOnce(false)
         .build());
```

In the at-least-once mode or if no guarantee is enabled, the transaction
is committed after each batch of messages: transactions are used for
performance as this is JMS' way to send messages in batches. Batches are
created from readily available messages so they incur minimal extra
latency.

##### Note

The XA transactions are implemented incorrectly in some brokers.
Specifically a prepared transaction is sometimes rolled back when the
client disconnects. The issue is tricky because the integration will
work during normal operation and the problem will only manifest if the
job crashes in a specific moment. Jet will even not detect it, only some
messages will be missing from the sink. To test your broker we provide a
tool, please go to [XA
tests](https://github.com/hazelcast/hazelcast-jet-contrib/tree/master/xa-test)
to get more information. This only applies to JMS sink, the source
doesn't use XA transactions.

#### Connection Handling

The JMS source and sink open one connection to the JMS server for each
member and each vertex. Then each parallel worker of the source creates
a session and a message consumer/producer using that connection.

IO failures are generally handled by the JMS client and do not cause the
connector to fail. Most of the clients offer a configuration parameter
to enable auto-reconnection, refer to the specific client documentation
for details.

### Apache Pulsar

>This connector is currently under incubation. For more
>information and examples, please visit the [GitHub repository](https://github.com/hazelcast/hazelcast-jet-contrib/tree/master/pulsar).

## In-memory Data Structures

Jet comes out of the box with some [in-memory distributed data
structures](data-structures) which can be used as a data source or a
sink. These sources are useful for caching sources or results to be used
for further processing, or acting as a glue between different data
pipelines.

### IMap

[IMap](data-structures) is a distributed in-memory key-value data
structure with a rich set of features such as indexes, querying and
persistence. With Jet, it can be used as both a batch or streaming data
source.

As a batch data source, it's very easy to use without the need for any other
configuration:

```java
IMap<String, User> userCache = jet.getMap("usersCache")
Pipeline p = Pipeline.create();
p.readFrom(Sources.map(userCache));
 .writeTo(Sinks.logger()));
```

#### Event Journal

The map can also be used as a streaming data source by utilizing its so
called _event journal_. The journal for a map is by default not enabled,
but can be explicitly enabled with a configuration option in
`hazelcast.yaml`:

```yaml
hazelcast:
  map:
    name_of_map:
      event-journal:
        enabled: true
        capacity: 100000
        time-to-live-seconds: 10
```

We can then modify the previous pipeline to instead stream the changes:

```java
IMap<String, User> userCache = jet.getMap("usersCache")
Pipeline p = Pipeline.create();
p.readFrom(Sources.mapJournal(userCache, START_FROM_OLDEST))
 .withIngestionTimestamps()
 .writeTo(Sinks.logger()));
```

By default, the source will only emit `ADDED` or `UPDATED` events and
the emitted object will have the key and the new value. You can change
to listen for all events by adding additional parameters to the source.

The event journal is fault tolerant and supports exactly-once
processing.

The capacity of the event journal is also an important consideration, as
having too little capacity will cause events to be dropped. Consider
also the capacity is for all the partition and not shared per partition.
For example, if there's many updates to just one key, with the default
partition count of `271` and journal size of `100,000` the journal only
has space for `370` events per partitions.

For a full example, please see the [Stream Changes From IMap tutorial.](../how-tos/stream-imap)

#### Map Sink

By default, the map sink expects items of type `Entry<Key, Value>` and
will simply replace the previous entries, if any. However, there are
variants of the map sink that allow you to do atomic updates to existing
entries in the map by making use of `EntryProcessor` objects.

The updating sinks come in three variants:

1. `mapWithMerging`, where you provide a function that computes the map
   value from the stream item and a merging function that gets called
   only if a value already exists in the map. This is similar to the way
   standard `Map.merge` method behaves. Here’s an example that
   concatenates String values:

```java
Pipeline p = Pipeline.create();
p.readFrom(Sources.<String, User>map("userCache"))
 .map(user -> entry(user.country(), user))
 .writeTo(Sinks.mapWithMerging("usersByCountry",
    e -> e.getKey(),
    e -> e.getValue().name(),
    (oldValue, newValue) -> oldValue + ", " + newValue)
  );
```

2. `mapWithUpdating`, where you provide a single updating function that
   always gets called. It will be called on the stream item and the
   existing value, if any. This can be used to add details to an
   existing object for example. This is similar to the way standard
   `Map.compute` method behaves. Here's an example that only updates a
   field:

```java
Pipeline p = Pipeline.create();
p.readFrom(Sources.<String, User>map("userCacheDetails"))
 .writeTo(Sinks.mapWithUpdating("userCache",
    e -> e.getKey(),
    (oldValue, entry) -> (oldValue != null ? oldValue.setDetails(entry.getValue) : null)
  );
```

3. `mapWithEntryProcessor`, where you provide a function that returns a
   full-blown `EntryProcessor` instance that will be submitted to the
   map. This is the most general variant. This example takes the
   values of the map and submits an entry processor that increments the
   values by 5:

```java
Pipeline p = Pipeline.create();
p.readFrom(Sources.<String, Integer>map("input"))
 .writeTo(Sinks.mapWithEntryProcessor("output",
    entry -> entry.getKey(),
    entry -> new IncrementEntryProcessor())
  );

static class IncrementEntryProcessor implements EntryProcessor<String, Integer, Integer> {
    @Override
    public Integer process(Entry<String, Integer> entry) {
        return entry.setValue(entry.getValue() + 5);
    }
}
```

> The variants above can be used to remove existing map entries by
setting their values to `null`. To put it another way, if these map sink
variants set the entry’s value to null, the entry will be removed
from the map.

#### Predicates and Projections

If your use case calls for some filtering and/or transformation of the
data you retrieve, you can optimize the pipeline by providing a
filtering predicate and an arbitrary transformation function to the
source connector itself and they’ll get applied before the data is
processed by Jet. This can be advantageous especially in the cases when
the data source is in another cluster. See the example below:

```java
IMap<String, Person> personCache = jet.getMap("personCache");
Pipeline p = Pipeline.create();
p.readFrom(Sources.map(personCache,
    Predicates.greaterEqual("age", 21),
    Projections.singleAttribute("name"))
);
```

### ICache

ICache is mostly equivalent to IMap, the main difference being that it's
compliant with the JCache standard API. As a sink, since `ICache`
doesn't support entry processors, only the default variant is available.

### IList

`IList` is a simple data structure which is ordered, and not
partitioned. All the contents of the `IList` will reside only on one
member.

The API for it is very limited, but is useful for simple prototyping:

```java
IList<Integer> inputList = jet.getList("inputList");
for (int i = 0; i < 10; i++) {
    inputList.add(i);
}

Pipeline p = Pipeline.create();
p.readFrom(Sources.list(inputList))
 .map(i -> "item-" + i)
 .writeTo(Sinks.list("resultList"));
```

List isn't suitable to use as a streaming sink because items are always
appended and eventually the member will run out of memory.

### Reliable Topic

Reliable Topic provides a simple pub/sub messaging API which can be
used as a data sink within Jet.

```java
jet.getReliableTopic("topic")
   .addMessageListener(message -> System.out.println(message));

Pipeline p = Pipeline.create();
p.readFrom(TestSources.itemStream(100))
  .withIngestionTimestamps()
  .writeTo(Sinks.reliableTopic("topic"));
```

A simple example is supplied above. For a more advanced version, also
see [Observables](#observable)

### Same vs. Different Cluster

It's possible to use the data structures that are part of the same Jet
cluster, and share the same memory and computation resources with
running jobs. For a more in-depth discussion on this topic, please see
the [In-memory Storage](../architecture/in-memory-storage) section.

Alternatively, Jet can also read from or write to data structures from
other Hazelcast or Jet clusters, using the _remote_ sinks and sources.
When reading or writing to remote sources, Jet internally creates a
client using the supplied configuration and will create connections to
the other cluster.

```java
ClientConfig cfg = new ClientConfig();
cfg.setClusterName("cluster-name");
cfg.getNetworkConfig().addAddress("node1.mydomain.com", "node2.mydomain.com");

Pipeline p = Pipeline.create();
p.readFrom(Sources.remoteMap("inputMap", cfg));
...
```

#### Compatibility

When reading or writing to remote sources, Jet internally creates a
client. This client uses the embedded IMDG version to connect to the
remote cluster. Starting with Hazelcast 3.6, Hazelcast server & client
versions are backward and forward compatible within the same major
version.

|Jet Version|Embedded IMDG Version|CompatibleVersions|
|:-----|:------------------|:-----------|
|Jet 3.0    |Hazelcast 3.12     |Hazelcast 3.y.z|
|Jet 3.1    |Hazelcast 3.12.1   |Hazelcast 3.6+|
|Jet 3.2    |Hazelcast 3.12.3   |Hazelcast 3.6+|
|Jet 3.2.1  |Hazelcast 3.12.5   |Hazelcast 3.6+|
|Jet 3.2.2  |Hazelcast 3.12.6   |Hazelcast 3.6+|
|Jet 4.0    |Hazelcast 4.0      |Hazelcast 4.y.z|
|Jet 4.1.1  |Hazelcast 4.0.1    |Hazelcast 4.y.z|
|Jet 4.2    |Hazelcast 4.0.1    |Hazelcast 4.y.z|
|Jet 4.3    |Hazelcast 4.0.3    |Hazelcast 4.y.z|

## Databases

Jet supports a wide variety of relational and NoSQL databases as a data
source or sink. While most traditional databases are batch oriented,
there's emerging techniques that allow to bridge the gap to streaming
which we will explore.

### JDBC

JDBC is a well-established database API supported by every major
relational (and many non-relational) database implementations including
Oracle, MySQL, PostgreSQL, Microsoft SQL Server. They provide libraries
called _JDBC drivers_ and every major database vendor will have this
driver available for either download or in a package repository such as
maven.

Jet is able to utilize these drivers both for sources and sinks and the
only step required is to add the driver to the `lib` folder of Jet or
submit the driver JAR along with the job.

In the simplest form, to read from a database you simply need to pass
a query:

```java
Pipeline p = Pipeline.create();
p.readFrom(Sources.jdbc("jdbc:mysql://localhost:3306/mysql",
    "SELECT * FROM person",
    resultSet -> new Person(resultSet.getInt(1), resultSet.getString(2))
)).writeTo(Sinks.logger());
```

Jet is also able to distribute a query across multiple nodes by
customizing the filtering criteria for each node:

```java
Pipeline p = Pipeline.create();
p.readFrom(Sources.jdbc(
    () -> DriverManager.getConnection("jdbc:mysql://localhost:3306/mysql"),
    (con, parallelism, index) -> {
        PreparedStatement stmt = con.prepareStatement(
              "SELECT * FROM person WHERE MOD(id, ?) = ?)");
        stmt.setInt(1, parallelism);
        stmt.setInt(2, index);
        return stmt.executeQuery();
    },
    resultSet -> new Person(resultSet.getInt(1), resultSet.getString(2))
)).writeTo(Sinks.logger());
```

The JDBC source only works in batching mode, meaning the query is only
executed once, for streaming changes from the database you can follow the
[Change Data Capture tutorial](../tutorials/cdc.md).

#### JDBC Data Sink

Jet is also able to output the results of a job to a database using the
JDBC driver by using an update query.

The supplied update query should be a parameterized query where the
parameters are set for each item:

```java
Pipeline p = Pipeline.create();
p.readFrom(KafkaSources.<Person>kafka(.., "people"))
 .writeTo(Sinks.jdbc(
         "REPLACE INTO PERSON (id, name) values(?, ?)",
         DB_CONNECTION_URL,
         (stmt, item) -> {
             stmt.setInt(1, item.id);
             stmt.setString(2, item.name);
         }
));
```

JDBC sink will automatically try to reconnect during database
connectivity issues and is suitable for use in streaming jobs. If you
want to avoid duplicate writes to the database, then a suitable
_insert-or-update_ statement should be used instead of `INSERT`, such as
`MERGE` or `REPLACE` or `INSERT .. ON CONFLICT ..`.

#### Fault Tolerance

The JDBC sink supports the exactly-once guarantee. It uses two-phase XA
transactions, the DML statements are committed consistently with the
last state snapshot. This greatly increases the latency, it is
determined by the snapshot interval: messages are visible to consumers
only after the commit. In order to make it work, instead of the JDBC URL
you have to use the variant with `Supplier<CommonDataSource>` and it
must return an instance of `javax.sql.XADataSource`, otherwise the job
will not start.

Here is an example for PostgreSQL:

```java
stage.writeTo(Sinks.jdbc("INSERT INTO " + tableName + " VALUES(?, ?)",
         () -> {
                 BaseDataSource dataSource = new PGXADataSource();
                 dataSource.setUrl("localhost:5432");
                 dataSource.setUser("user");
                 dataSource.setPassword("pwd");
                 dataSource.setDatabaseName("database1");
                 return dataSource;
         },
         (stmt, item) -> {
             stmt.setInt(1, item.getKey());
             stmt.setString(2, item.getValue());
         }
 ));
```

##### Note

The XA transactions are implemented incorrectly in some databases.
Specifically a prepared transaction is sometimes rolled back when the
client disconnects. The issue is tricky because the integration will
work during normal operation and the problem will only manifest if the
Jet job crashes in a specific moment. Jet will even not detect it, only
some records will be missing from the target database. To test your
broker we provide a tool, please go to [XA
tests](https://github.com/hazelcast/hazelcast-jet-contrib/tree/master/xa-test)
to get more information. This only applies to the JDBC sink, the source
doesn't use XA transactions.

### Change Data Capture (CDC)

Change Data Capture (CDC) refers to the process of observing changes
made to a database and extracting them in a form usable by other
systems, for the purposes of replication, analysis and many more.

Change Data Capture is especially important to Jet, because it allows
for the _streaming of changes from databases_, which can be efficiently
processed by Jet.

Implementation of CDC in Jet is based on
[Debezium](https://debezium.io/). Jet offers a generic Debezium source
which can handle CDC events from [any database supported by
Debezium](https://debezium.io/documentation/reference/1.1/connectors/index.html),
but we're also striving to make CDC sources first class citizens in Jet.
The ones for MySQL & PostgreSQL already are (since Jet version 4.2).

Setting up a streaming source of CDC data is just the matter of pointing
it at the right database via configuration:

```java
Pipeline pipeline = Pipeline.create();
pipeline.readFrom(
    MySqlCdcSources.mysql("customers")
            .setDatabaseAddress("127.0.0.1")
            .setDatabasePort(3306)
            .setDatabaseUser("debezium")
            .setDatabasePassword("dbz")
            .setClusterName("dbserver1")
            .setDatabaseWhitelist("inventory")
            .setTableWhitelist("inventory.customers")
            .build())
    .withNativeTimestamps(0)
    .writeTo(Sinks.logger());
```

(For an example of how to actually make use of CDC data see [our
tutorial](../tutorials/cdc)).

In order to make it work though, the databases need to be properly
configured too, have features essential for CDC enabled. For details see
the [CDC Deployment Guide](../operations/cdc.md).

#### CDC Connectors

As of Jet version 4.5.4 we have following types of CDC sources:

* [DebeziumCdcSources](/javadoc/4.5.4/com/hazelcast/jet/cdc/DebeziumCdcSources.html):
  generic source for all databases supported by Debezium
* [MySqlCdcSources](/javadoc/4.5.4/com/hazelcast/jet/cdc/mysql/MySqlCdcSources.html):
  specific, first class Jet CDC source for MySQL databases (also based
  on Debezium, but benefiting the full range of convenience Jet can
  additionally provide)
* [PostgresCdcSources](/javadoc/4.5.4/com/hazelcast/jet/cdc/postgres/PostgresCdcSources.html):
  specific, first class Jet CDC source for PostgreSQL databases (also based
  on Debezium, but benefiting the full range of convenience Jet can
  additionally provide)

#### CDC Fault Tolerance

CDC sources offer at least-once processing guaranties. The source
periodically saves the database write ahead log offset for which it had
dispatched events and in case of a failure/restart it will replay all
events since the last successfully saved offset.

Unfortunately, however, there is no guarantee that the last saved offset
is still in the database changelog. Such logs are always finite and
depending on the DB configuration can be relatively short, so if the CDC
source has to replay data for a long period of inactivity, then there
can be a data loss. With careful management though we can say that
at-least once guarantee can practically be provided.

#### CDC Sinks

Change data capture is a source-side functionality in Jet, but we also
offer some specialized sinks that simplify applying CDC events to an
IMap, which gives you the ability to reconstruct the contents of the
original database table. The sinks expect to receive `ChangeRecord`
objects and apply your custom functions to them that extract the key and
the value that will be applied to the target IMap.

For example, a sink mapping CDC data to a `Customer` class and
maintaining a map view of latest known email addresses per customer
(identified by ID) would look like this:

```java
Pipeline p = Pipeline.create();
p.readFrom(source)
 .withoutTimestamps()
 .writeTo(CdcSinks.map("customers",
    r -> r.key().toMap().get("id"),
    r -> r.value().toObject(Customer.class).email));
```

> NOTE: The key and value functions have certain limitations. They can
> be used to map only to objects which the IMDG backend can deserialize,
> which unfortunately doesn't include user code submitted as a part of
> the Jet job. So in the above example it's OK to have `String` email
> values, but we wouldn't be able to use `Customer` directly.
>
> If user code has to be used, then the problem can be solved with the
> help of IMDG's "User Code Deployment" feature. Example configs for
> that can be seen in our [CDC Join Tutorial](../tutorials/cdc-join#7-start-hazelcast-jet).

### Elasticsearch

Elasticsearch is a popular fulltext search engine. Hazelcast Jet can
use it both as a source and a sink.

#### Dependency

To use the Elasticsearch connector, make sure the
`hazelcast-jet-elasticsearch-7` module is present in the
`lib` folder and add the following dependency to your application:

<!--DOCUSAURUS_CODE_TABS-->

<!--Gradle-->

```groovy
compile 'com.hazelcast.jet:hazelcast-jet-elasticsearch-7:4.5.4'
```

<!--Maven-->

```xml
<dependency>
  <groupId>com.hazelcast.jet</groupId>
  <artifactId>hazelcast-jet-elasticsearch-7</artifactId>
  <version>4.5.4</version>
</dependency>
```

> For Elasticsearch version 6 and 5 there are separate modules
> `hazelcast-jet-elasticsearch-6` and `hazelcast-jet-elasticsearch-5`.
> Each module includes Elasticsearch client compatible with given major
> version of Elasticsearch. The connector API is the same between
> different versions, apart from a few minor differences where we
> surface the API of Elasticsearch client. See the JavaDoc for any
> such differences. The jars are available as separate downloads on the
> [download page](/download) or in Maven Central.

#### Source

The Elasticsearch connector source provides a builder and several
convenience factory methods. Most commonly one needs to provide:

* A client supplier function, which returns a configured instance of
 RestClientBuilder (see [Elasticsearch documentation](https://www.elastic.co/guide/en/elasticsearch/client/java-rest/current/java-rest-low-usage-initialization.html#java-rest-low-usage-initialization)),
* A search request supplier specifying a query to Elasticsearch,
* A mapping function from `SearchHit` to a desired type.

Example using a factory method:

```java
BatchSource<String> elasticSource = ElasticSources.elastic(
    () -> client("user", "password", "host", 9200),
    () -> new SearchRequest("my-index"),
    hit -> (String) hit.getSourceAsMap().get("name")
);
```

For all configuration options use the builder:

```java
BatchSource<String> elasticSource = new ElasticSourceBuilder<String>()
        .name("elastic-source")
        .clientFn(() -> RestClient.builder(new HttpHost(
                "localhost", 9200
        )))
        .searchRequestFn(() -> new SearchRequest("my-index"))
        .optionsFn(request -> RequestOptions.DEFAULT)
        .mapToItemFn(hit -> hit.getSourceAsString())
        .slicing(true)
        .build();
```

By default, the connector uses a single scroll to read data from
Elasticsearch - there is only a single reader on a single node in the
whole cluster.

Slicing can be used to parallelize reading from an index with more
shards. Number of slices equals to globalParallelism.

If Hazelcast Jet nodes and Elasticsearch nodes are located on the same
machines then the connector will use co-located reading, avoiding the
overhead of physical network.

##### Failure Scenario Considerations

The connector uses retry capability of the underlying Elasticsearch
client. This allows the connector to handle some transient network
issues but it doesn't cover all cases.

The source uses Elasticsearch's [Scroll API](https://www.elastic.co/guide/en/elasticsearch/client/java-rest/current/java-rest-high-search-scroll.html).
The scroll context is stored on a node with the primary shard. If this
node crashes, the search context is lost and the job can't reliably read
all documents, so the job fails.

If there is a network issue between Jet and Elasticsearch the
Elasticsearch client retries the request, allowing the job to continue.

However, there is an edge case where the scroll request is processed by
the Elasticsearch server, moves the scroll cursor forward, but the
response is lost. The client then retries and receives the next page,
effectively skipping the previous page. The recommended way to handle
this is to check the number of processed documents after the job
finishes, possibly restart the job when not all documents are read.

These are known limitations of Elasticsearch Scroll API. There is
an [ongoing work](https://github.com/elastic/elasticsearch/pull/56480)
on Elasticsearch side to fix these issues.

#### Sink

The Elasticsearch connector sink provides a builder and several
convenience factory methods. Most commonly you need to provide:

* A client supplier, which returns a configured instance of
 RestHighLevelClient (see
 [Elasticsearch documentation](https://www.elastic.co/guide/en/elasticsearch/client/java-rest/current/java-rest-low-usage-initialization.html#java-rest-low-usage-initialization)),

* A mapping function to map items from the pipeline to an instance of
 one of `IndexRequest`, `UpdateRequest` or `DeleteRequest`.

* Suppose type of the items in the pipeline is `Map<String, Object>`, the
 sink can be created using

```java
Sink<Map<String, Object>> elasticSink = ElasticSinks.elastic(
    () -> client("user", "password", "host", 9200),
    item -> new IndexRequest("my-index").source(item)
);
```

For all configuration options use the builder:

```java
Sink<Map<String, Object>> elasticSink = new ElasticSinkBuilder<Map<String, Object>>()
    .name("elastic-sink")
    .clientFn(() -> RestClient.builder(new HttpHost(
            "localhost", 9200
    )))
    .bulkRequestSupplier(BulkRequest::new)
    .mapToRequestFn((map) -> new IndexRequest("my-index").source(map))
    .optionsFn(request -> RequestOptions.DEFAULT)
    .build();
```

The Elasticsearch sink doesn't implement co-located writing. To achieve
maximum write throughput provide all nodes to the `RestClient`
and configure parallelism.

##### Failure Scenario Considerations

The sink connector is able to handle transient network failures,
failures of nodes in the cluster and cluster changes, e.g., scaling up.

Transient network failures between Jet and Elasticsearch cluster are
handled by retries in the Elasticsearch client.

The worst case scenario is when a master node containing a primary of a
shard fails.

First, you need to set `BulkRequest.waitForActiveShards(int)` to ensure
that a document is replicated to at least some replicas. Also, you can't
use the auto-generated ids and need to set the document id manually to
avoid duplicate records.

Second, you need to make sure new master node and primary shard is
allocated before the client times out. This involves:

* configuration of the following properties on the client:

  ```text
  org.apache.http.client.config.RequestConfig.Builder.setConnectionRequestTimeout
  org.apache.http.client.config.RequestConfig.Builder.setConnectTimeout
  org.apache.http.client.config.RequestConfig.Builder.setSocketTimeout
  ```

* and configuration of the following properties in the Elasticsearch
  cluster:

  ```text
  cluster.election.max_timeout
  cluster.fault_detection.follower_check.timeout
  cluster.fault_detection.follower_check.retry_count
  cluster.fault_detection.leader_check.timeout
  cluster.fault_detection.leader_check.retry_count
  cluster.follower_lag.timeout
  transport.connect_timeout
  transport.ping_schedule
  network.tcp.connect_timeout
  ```

For details see Elasticsearch documentation section on
[cluster fault detection](https://www.elastic.co/guide/en/elasticsearch/reference/current/cluster-fault-detection.html).

### MongoDB

>This connector is currently under incubation. For more
>information and examples, please visit the [GitHub repository](https://github.com/hazelcast/hazelcast-jet-contrib/tree/master/mongodb).

### InfluxDB

>This connector is currently under incubation. For more
>information and examples, please visit the [GitHub repository](https://github.com/hazelcast/hazelcast-jet-contrib/tree/master/influxdb).

### Redis

>This connector is currently under incubation. For more
>information and examples, please visit the [GitHub repository](https://github.com/hazelcast/hazelcast-jet-contrib/tree/master/redis).

## Miscellaneous

### Test Sources

Test sources make it convenient to get started with Jet without having
to use an actual data source. They can also be used for unit testing
different pipelines where you can expect a more deterministic import.

#### Batch

The `items` source offers a simple batch source where the supplied list
of items are output:

```java
Pipeline p = Pipeline.create();
p.readFrom(TestSources.items(1, 2, 3, 4))
 .writeTo(Sinks.logger());
```

This pipeline will emit the following items, and then the job will terminate:

```text
12:33:01.780 [ INFO] [c.h.j.i.c.W.loggerSink#0] 1
12:33:01.780 [ INFO] [c.h.j.i.c.W.loggerSink#0] 2
12:33:01.780 [ INFO] [c.h.j.i.c.W.loggerSink#0] 3
12:33:01.780 [ INFO] [c.h.j.i.c.W.loggerSink#0] 4
```

#### Streaming

The test streaming source emits an infinite stream of `SimpleEvent`s at
the requested rate (in this case, 10 items per second):

```java
p.readFrom(TestSources.itemStream(10))
 .withNativeTimestamp(0)
 .writeTo();
```

After submitting this job, you can expect infinite output like:

```text
12:33:36.774 [ INFO] [c.h.j.i.c.W.loggerSink#0] SimpleEvent(timestamp=12:33:36.700, sequence=0)
12:33:36.877 [ INFO] [c.h.j.i.c.W.loggerSink#0] SimpleEvent(timestamp=12:33:36.800, sequence=1)
12:33:36.976 [ INFO] [c.h.j.i.c.W.loggerSink#0] SimpleEvent(timestamp=12:33:36.900, sequence=2)
12:33:37.074 [ INFO] [c.h.j.i.c.W.loggerSink#0] SimpleEvent(timestamp=12:33:37.000, sequence=3)
12:33:37.175 [ INFO] [c.h.j.i.c.W.loggerSink#0] SimpleEvent(timestamp=12:33:37.100, sequence=4)
12:33:37.274 [ INFO] [c.h.j.i.c.W.loggerSink#0] SimpleEvent(timestamp=12:33:37.200, sequence=5)
```

Each `SimpleEvent` has a sequence which is monotonically increased and
also a timestamp which is derived from `System.currentTimeMillis()`.
For more information using these sources in a testing environment, refer
to the [Testing](testing) section.

### Observables

A Jet pipeline always expects to write the results somewhere. Sometimes
the job submitter is different than the one reading or processing the
results of a pipeline, but sometimes it can be the same, for example if the
job is a simple ad-hoc query. In this case Jet offers a special type of
construct called an `Observable`, which can be used as a sink.

For example, imagine the following pipeline:

```java
JetInstance jet = Jet.bootstrappedInstance();
Observable<SimpleEvent> observable = jet.newObservable();
observable.addObserver(e -> System.out.println("Printed from client: " + e));

Pipeline pipeline = p.create();
p.readFrom(TestSources.itemStream(5))
 .withIngestionTimestamps()
 .writeTo(Sinks.observable(observable));
try {
  jet.newJob(pipeline).join();
} finally {
  observable.destroy();
}
```

When you run this pipeline, you'll see the following output:

```text
Printed from client: SimpleEvent(timestamp=12:36:53.400, sequence=28)
Printed from client: SimpleEvent(timestamp=12:36:53.600, sequence=29)
Printed from client: SimpleEvent(timestamp=12:36:53.800, sequence=30)
Printed from client: SimpleEvent(timestamp=12:36:54.000, sequence=31)
Printed from client: SimpleEvent(timestamp=12:36:54.200, sequence=32)
Printed from client: SimpleEvent(timestamp=12:36:54.400, sequence=33)
Printed from client: SimpleEvent(timestamp=12:36:54.600, sequence=34)
Printed from client: SimpleEvent(timestamp=12:36:54.800, sequence=35)
Printed from client: SimpleEvent(timestamp=12:36:55.000, sequence=36)
```

You can see that the printed output is actually on the client, and not
on the server. Jet internally uses Hazelcast's `Ringbuffer` to create a
temporary buffer to write the results into and these are then fetched by
the client:

>It's worth noting that `Ringbuffer` may lose events, if they
>are being produced at a higher-rate than the clients can consume it. There
>will be a warning logged in such cases. You can also configure the
>capacity using the `setCapacity()` method on the `Observable`.

`Observable` can also implement `onError` and `onComplete` methods to
get notified of job completion and errors.

#### Futures

`Observable` also support a conversion to a future to collect the
results.

For example, to collect the job results into a list, you can use the
following pattern:

```java
JetInstance jet = Jet.bootstrappedInstance();
Observable<String> observable = jet.newObservable();

Pipeline p = Pipeline.create();
p.readFrom(TestSources.items("a", "b", "c", "d"))
 .writeTo(Sinks.observable(observable));

Future<List<String>> future = observable.toFuture(
    s -> s.collect(Collectors.toList())
);
jet.newJob(p);

try {
  List<String> results = future.get();
  for (String result : results) {
    System.out.println(result);
  }
} finally {
  observable.destroy();
}
```

#### Cleanup

As `Observable`s are backed by `Ringbuffer`s stored in the cluster which
should be cleaned up by the client, once they are no longer necessary
using the `destroy()` method. If the Observable isn’t destroyed, the
memory used by it will be not be recovered by the cluster. It's possible
to get a list of all observables using the
`JetInstance.getObservables()` method.

### Socket

The socket sources and sinks opens a TCP socket to the supplied address
and either read from or write to the socket. The sockets are text-based
and may only read or write text data.

A simple example of the source is below:

```java
Pipeline p = Pipeline.create();
p.readFrom(Sources.socket("localhost", 8080, StandardCharsets.UTF_8))
 .withoutTimestamps()
 .map(line -> /* parse line */)
 .writeTo(Sinks.logger());
```

This will connect to a socket on port 8080 and wait to receive some
lines of text, which will be sent as an item for the next step in the
pipeline to process.

Please note that Jet itself will not create any server sockets, this
should be handled outside of the Jet process itself.

When used as a sink, it will send a line of text for each input item,
similar to how the source works:

```java
Pipeline p = Pipeline.create();
p.readFrom(Sources.itemStream(10))
 .withoutTimestamps()
 .map(e -> e.toString())
 .writeTo(Sinks.socket("localhost", 8080));
```

Any disconnections for both source and sink will cause the job to fail,
so this source is mostly aimed for simple IPC or testing.

### HTTP Listener

>This connector is currently under incubation. For more
>information and examples, please visit the [GitHub repository](https://github.com/hazelcast/hazelcast-jet-contrib/tree/master/http).

### Twitter

>This connector is currently under incubation. For more
>information and examples, please visit the [GitHub repository](https://github.com/hazelcast/hazelcast-jet-contrib/tree/master/twitter).

## Summary

### Sources

Below is a summary of various sources and where to find them. Some
sources are batch and some are stream oriented. The processing guarantee
is only relevant for streaming sources, as batch jobs should just be
restarted in face of an intermittent failure.

|source|artifactId (module)|batch/stream|guarantee|
|:-----|:------------------|:-----------|:--------|
|`AvroSources.files`|`hazelcast-jet-avro (avro)`|batch|N/A|
|`DebeziumCdcSources.debezium`|`hazelcast-jet-cdc-debezium (cdc-debezium)`|stream|at-least-once|
|`ElasticSources.elastic`|`hazelcast-jet-elasticsearch-5 (elasticsearch-5)`|batch|N/A|
|`ElasticSources.elastic`|`hazelcast-jet-elasticsearch-6 (elasticsearch-6)`|batch|N/A|
|`ElasticSources.elastic`|`hazelcast-jet-elasticsearch-7 (elasticsearch-7)`|batch|N/A|
|`HadoopSources.inputFormat`|`hazelcast-jet-hadoop (hadoop)`|batch|N/A|
|`KafkaSources.kafka`|`hazelcast-jet-kafka (kafka)`|stream|exactly-once|
|`KinesisSources.kinesis`|`hazelcast-jet-kinesis (kinesis)`|stream|exactly-once|
|`MySqlCdcSources.mysql`|`hazelcast-jet-cdc-mysql (cdc-mysql)`|stream|exactly-once|
|`PostgresCdcSources.postgres`|`hazelcast-jet-cdc-postgres (cdc-postgres)`|stream|exactly-once|
|`PulsarSources.pulsarConsumer`|`hazelcast-jet-contrib-pulsar`|stream|N/A|
|`PulsarSources.pulsarReader`|`hazelcast-jet-contrib-pulsar`|stream|exactly-once|
|`S3Sources.s3`|`hazelcast-jet-s3 (s3)`|batch|N/A|
|`Sources.cache`|`hazelcast-jet`|batch|N/A|
|`Sources.cacheJournal`|`hazelcast-jet`|stream|exactly-once|
|`Sources.files`|`hazelcast-jet`|batch|N/A|
|`Sources.fileWatcher`|`hazelcast-jet`|stream|none|
|`Sources.json`|`hazelcast-jet`|batch|N/A|
|`Sources.jsonWatcher`|`hazelcast-jet`|stream|none|
|`Sources.jdbc`|`hazelcast-jet`|batch|N/A|
|`Sources.jmsQueue`|`hazelcast-jet`|stream|exactly-once|
|`Sources.list`|`hazelcast-jet`|batch|N/A|
|`Sources.map`|`hazelcast-jet`|batch|N/A|
|`Sources.mapJournal`|`hazelcast-jet`|stream|exactly-once|
|`Sources.socket`|`hazelcast-jet`|stream|none|
|`TestSources.items`|`hazelcast-jet`|batch|N/A|
|`TestSources.itemStream`|`hazelcast-jet`|stream|none|

### Sinks

Below is a summary of various sinks and where to find them. All sources
may operate in batch mode, but only some of them are suitable for
streaming jobs, this is indicated below. As with sources, the processing
guarantee is only relevant for streaming jobs. All streaming sinks by
default support at-least-once guarantee, but only some of them support
exactly-once. If using idempotent updates, you can ensure exactly-once
processing even with at-least-once sinks.

|sink|artifactId (module)|streaming support|guarantee|
|:---|:------------------|:--------------|:-------------------|
|`AvroSinks.files`|`hazelcast-jet-avro (avro)`|no|N/A|
|`CdcSinks.map`|`hazelcast-jet-cdc-debezium (cdc-debezium)`|yes|at-least-once|
|`ElasticSinks.elastic`|`hazelcast-jet-elasticsearch-5 (elasticsearch-5)`|yes|at-least-once|
|`ElasticSinks.elastic`|`hazelcast-jet-elasticsearch-6 (elasticsearch-6)`|yes|at-least-once|
|`ElasticSinks.elastic`|`hazelcast-jet-elasticsearch-7 (elasticsearch-7)`|yes|at-least-once|
|`HadoopSinks.outputFormat`|`hazelcast-jet-hadoop (hadoop)`|no|N/A|
|`KafkaSinks.kafka`|`hazelcast-jet-kafka (kafka)`|yes|exactly-once|
|`KinesisSinks.kinesis`|`hazelcast-jet-kinesis (kinesis)`|yes|at-least-once|
|`PulsarSources.pulsarSink`|`hazelcast-jet-contrib-pulsar`|yes|at-least-once|
|`S3Sinks.s3`|`hazelcast-jet-s3 (s3)`|no|N/A|
|`Sinks.cache`|`hazelcast-jet`|yes|at-least-once|
|`Sinks.files`|`hazelcast-jet`|yes|exactly-once|
|`Sinks.json`|`hazelcast-jet`|yes|exactly-once|
|`Sinks.jdbc`|`hazelcast-jet`|yes|exactly-once|
|`Sinks.jmsQueue`|`hazelcast-jet`|yes|exactly-once|
|`Sinks.list`|`hazelcast-jet`|no|N/A|
|`Sinks.map`|`hazelcast-jet`|yes|at-least-once|
|`Sinks.observable`|`hazelcast-jet`|yes|at-least-once|
|`Sinks.reliableTopic`|`hazelcast-jet`|yes|at-least-once|
|`Sinks.socket`|`hazelcast-jet`|yes|at-least-once|

## Custom Sources and Sinks

If Jet doesn’t natively support the data source/sink you need, you can
build a connector for it yourself by using the
[SourceBuilder](/javadoc/4.5.4/com/hazelcast/jet/pipeline/SourceBuilder.html)
and
[SinkBuilder](/javadoc/4.5.4/com/hazelcast/jet/pipeline/SinkBuilder.html).

### SourceBuilder

To make a custom source connector you need two basic ingredients:

* a _context_ object that holds all the resources and state you need
  to keep track of
* a stateless function, _`fillBufferFn`_, taking two parameters: the
  state object and a buffer object provided by Jet

Jet repeatedly calls `fillBufferFn` whenever it needs more data items.
Optimally, the function will fill the buffer with the items it can
acquire without blocking. A hundred items at a time is enough to
eliminate any per-call overheads within Jet. The function is allowed to
block as well, but taking longer than a second to complete can have
negative effects on the overall performance of the processing pipeline.

In the following examples we build a simple batch source that emits
the lines of a file:

```java
BatchSource<String> fileSource = SourceBuilder
    .batch("file-source", x -> new BufferedReader(new FileReader("input.txt")))
    .<String>fillBufferFn((in, buf) -> {
        String line = in.readLine();
        if (line != null) {
            buf.add(line);
        } else {
            buf.close();
        }
    })
    .destroyFn(BufferedReader::close)
    .build();
```

For a more involved example (which reads data in _batches_ for
efficiency, deals with _unbounded_ data, emits _timestamps_, is
_distributed_ and _fault tolerant_ see the
[Custom Batch Sources](../how-tos/custom-batch-source.md
) and
[Custom Stream Sources](../how-tos/custom-stream-source.md)
tutorials).

### SinkBuilder

To make your custom sink connector you need two basic ingredients:

* a _context_ object that holds all the resources and state you need
  to keep track of
* a stateless function, _`receiveFn`_, taking two parameters: the state
  object and a data item sent to the sink

In the following example we build a simple sink which writes the
`toString()` form of `Object`s to a file:

```java
Sink<Object> sink = sinkBuilder(
    "file-sink", x -> new PrintWriter(new FileWriter("output.txt")))
    .receiveFn((out, item) -> out.println(item.toString()))
    .destroyFn(PrintWriter::close)
    .build();
```

For a more involved example, covering issues like _batching_,
_distributiveness_ and _fault tolerance_, see the
[Custom Sinks](../how-tos/custom-sink.md) tutorial).
