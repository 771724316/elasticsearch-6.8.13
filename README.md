# 原生的collapse不支持根据规则筛选代表数据、改了Es的源码做了支持，数据根据字段做折叠去重，代表数据可以根据规则进行筛选。<br/>编译Elasticsearch源码、然后改源码、替换原始jar
## 一、构建、编译源码参考以下文章
### 1、http://www.54tianzhisheng.cn/2018/08/05/es-code01/
### 2、https://www.cnblogs.com/shwang/p/12620389.html
### 3、http://ddrv.cn/a/315312

## 二、 改完源码打包的时候、在idea右侧点击Gradle、进入server模块、按顺序点击 task->build->jar 、打包完成后进入idea项目工作空间、进入elasticsearch-6.8.13\server\build\distributions 里面有个jar、这个就是最新编译的jar、把jar放到可执行的es目录的lib文件夹内、替换掉原始es的jar包



## 主要改了以下几个类
### 1、server/src/main/java/org/apache/lucene/search/grouping/CollapseTopFieldDocs2.java
### 2、server/src/main/java/org/apache/lucene/search/grouping/MyDataComparator.java
### 3、server/src/main/java/org/elasticsearch/search/collapse/CollapseBuilder.java
"# elasticsearch-6.8.13" 



# 测试数据
[Es测试数据](https://github.com/771724316/elasticsearch-6.8.13/blob/master/test_data.txt)
