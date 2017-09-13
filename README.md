# Lab for Java Deserialization Vulnerabilities

This content is related to the paper written for the 14th edition of H2HC magazine. 
See full paper in: https://www.h2hc.com.br/revista/
>Um overview sobre as bases das falhas de desserialização nativa em ambientes Java (JVM)

>An overview of the bases of native deserialization failures in Java environments (JVM)

Content
--
The lab contains code samples that help you understand deserialization vulnerabilities and how gadget chains exploit them. 
The goal is to provide a better understanding so that you can develop new payloads and/or better design your environments.

There is also a vulnerable testing application (VulnerableHTTPServer.java), which helps you test your payloads.

Usage Examples
--
First of all you need to read the full paper. Then review the sample codes and use the vulnerable testing application to understand how payloads work.


######Getting codes:

```
$ git clone https://github.com/joaomatosf/JavaDeserH2HC.git
$ cd JavaDeserH2HC
```

######Compiling and executing Vulnerable Web Application:

```
$ javac VulnerableHTTPServer.java
$ java -cp .:commons-collections-3.2.1.jar VulnerableHTTPServer
```


```==================================================================
Simple Java HTTP Server for Deserialization Lab v0.01
https://github.com/joaomatosf/JavaDeserH2HC

JRE Version: 1.8.0_131
------------------------------------------------------------------
You can inject java serialized objects in the following formats:

1) Binary in HTTP POST (ie \xAC\xED). Ex:
    $ curl 127.0.0.1:8000/ --data-binary @ObjectFile.ser

2) Base64 or Gzip+Base64 via HTTP POST parameters (eg. ViewState=rO0 or ViewState=H4sI....). Ex:
    $ echo -n "H4sICAeH..." | curl 127.0.0.1:8000/ -d @-
    $ echo -n "rO0ABXNy..." | curl 127.0.0.1:8000/ -d @-

3) Base64 or Gzip+Base64 in cookies (eg. Cookie: Jsessionid=rO0... or Cookie: Jsessionid=H4sI...). Ex:
    $ curl 127.0.0.1:8000/ -H "cookie: jsessionid=H4sICAeH..."
    $ curl 127.0.0.1:8000/ -H "cookie: jsessionid=rO0ABXNy..."

OBS: To test gadgets in specific libraries, run with -cp param. Ex:
$ java -cp .:commons-collections-3.2.1.jar VulnerableHTTPServer
==================================================================
[INFO]: Listening on port 8000
```