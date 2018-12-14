#结构化搜索
##term filter
###实验数据源
``` json
POST /forum/article/_bulk
{"index":{"_id":1}}
{"articleId":"XHDK-A-1293-#fJ3", "userId":1, "hidden":false, "postDate":"2018-01-01"}
{"index":{"_id":2}}
{"articleId":"KDKE-B-9947-#kL5", "userId":1, "hidden":false, "postDate":"2018-01-02"}
{"index":{"_id":3}}
{"articleId":"JODL-X-1937-#pV7", "userId":2, "hidden":false, "postDate":"2018-01-01"}
{"index":{"_id":3}}
{"articleId":"QQPX-R-3956-#aD8", "userId":2, "hidden":true, "postDate":"2018-01-02"}
```

###查看mapping
```aidl
GET /forum/_mapping/article
{
  "forum": {
    "mappings": {
      "article": {
        "properties": {
          "articleId": {
            "type": "text",
            "fields": {
              "keyword": {
                "type": "keyword",
                "ignore_above": 256
              }
            }
          },
          "hidden": {
            "type": "boolean"
          },
          "postDate": {
            "type": "date"
          },
          "userId": {
            "type": "long"
          }
        }
      }
    }
  }
}

```
**type=text，默认会设置两个field，一个是field本身，比如articleId，是分词的，还有一个是field.keyword，articleId.keyword，默认是不分词的，最多会保留256**

###分词
```aidl
GET /forum/_analyzer
{ "field":"articleId", "text":"XHDK-A-1985-#aJ6"}

```
**默认analyzer的text类型是field，建立倒排索引的时候，就会对所有的articleId分词，分词以后原本的articleId就没有了，只有分词后的各个word存在于倒排索引中。term是不对搜索文本分词的，
XHDK-A-1985-#aJ6--> XHDK-A-1985-#aJ6, 但是articleId建立索引的时候就已经变成了单个word，所以是不能搜索到的。**
**article.keyword是es最新版本内置建立的field，就是不分词的，所以一个articleId会建立两次索引，一次是自己本身，是要分词的，分词之后放入倒排索引；另一次是基于articleId.keyword，不分词，最多保留256个字符，最多，直接一个字符串字符串放入倒排索引。
所以term  filter，对text过滤，可以先考虑使用内置的field.keyword进行匹配，但是有一个问题，默认保留256个字符，所以尽可能还是手动建立索引，指定为not_analyzed。在最新的es中可以不指定not_analyzed，可以将type设置为keyword即可。**
```aidl
DELETE /forum
PUT /forum
{"mappings" :{"article":{"properties":{"articleId":{"type":"keyword"}}}}}

```

##_filter执行原理（bitset机制和cache机制）
- 在倒排索引中查找搜索串，获取document list
- 为每个在倒排索引中搜索得到的结果构建一个bitset   [0,0,0,0,1,0,1]
- 遍历每个过滤条件对应的bitset，优先从最稀疏的开始搜索，查找满足所有条件的document
- cachingbitset，跟踪query，在最近256个query中超过一定次数的过滤条件，缓存其bitset，对于小segment(<1000或<3%)，不缓存bitset
- filter在query之前执行，先尽量过滤掉尽可能多的数据
- 如果document有新增或者修改，那么cached bitset会自动更新
- 以后只要有相同的filter条件，会直接使用这个过滤条件对应的cached bitset


**
(1) 使用bulkProcess时为了保证有足够的时间让异步执行的线程返回，需要在主线程中做适当的等待
(2) 日期range  
- QueryBuilders.rangeQuery("postDate").gt("2018-12-08||-30d")
- QueryBuilders.rangeQuery("postDate").gt("now-30d")
(3)匹配度必须使用百分数。小数和除法不识别 QueryBuilders.matchQuery("title", "java elasticsearch spark hadoop").minimumShouldMatch("75%")
(4)在field上指定权重错误，需要但对于使用queryBuilder指定 QueryBuilders.multiMatchQuery("java beginner", "title^3.0", "content")
(5)minimum_should_match:去长尾（long tail）。长尾：比如搜索多个关键词，但是很多结果都只匹配一个关键词，这些结果就是长尾。可以控制搜索结果的精准度，只有匹配一定数量的关键词才能返回。
(6)连续使用多个doc，只会有最后一个doc被添加   processor.add(new UpdateRequest().index(FORUM).type(ARTICLE).id("1").doc("author_first_name", "Peter").doc("author_last_name", "Smith"));
**


##term + bool实现multiword
```json
    {
        "query":{
            "match":{
                "title": "java elasticsearch"
            }
       }
    }
===转化为====>
    {
        "query":{
            "bool":{
                "should":[{
                        "term":{"title": "java"}
                    },
                    {
                        "term":{"title":"java"}
                   }
                ]
           }
        }
    }
```
##多shard长惊喜relevance score不准确
1.多shard场景下relevance score不准确问题：如果一个index下有多个shard，可能搜索结果不准确。
2.如何解决？
   - 生产环境下，数据量大，尽可能的实现均衡负载
   - 测试环境下，将索引的primary shard设置为1ge
   - 测试环境下，搜素附带search_type=dfs_query_then_fetch参数，会将local IDF取出来计算global IDF

##多字段搜索相关度评分
> 计算每个document的relevance score，每个query的分数，乘以matched query的数量，除以总的query数量
{"match" : {"title":"java solution"}  1.2
{"match":{"content":"java solution"}} 1.1
{"match":{"desc":java solution"} 0
(1.2 + 1.1 + 0) * 2 / 3

##best field策略 dis_max
> best field策略：搜索到的结果，应该是某一个field中匹配到了尽可能多的关键字，被排在前面，而不是尽可能多的field匹配到少数的关键词，排在前面

##most field策略
> 尽可能返回更多field匹配到的某个关键词的doc，优先返回
> most_field 与 best_field的区别
- best_field是对多个field进行搜索，挑选某个field匹配度最高的那个分数，同时在多个query最高分相同的情况下，在一定程度上考虑其他query的分数。简单说，对多个field进行搜索，就想搜索到某一个field尽可能包含更多关键词的数据
    - 优点：通过best_field策略，以及综合考虑其他field，还有minimum_should_match支持，可以尽可能精准地将匹配的结果推送到最前面。
    - 缺点 除了那些精准搜索的结果，其他差不大的结果，排序结果不是太均匀，没有什么区分度
- most_field 综合多个field一起进行搜索，尽可能多让所有的field的query参与到总分数的计算中，此时结果不一定精准，某一个document的一个field包含更多的关键字，但是因为其他的document有更多的field匹配到了，所以排在了前面，所以需要建立类似sub_title.std这样的搜索。
    - 优点：将尽可能匹配更多的field的结果推送到前面，整个排序结果是比较均匀的
    - 缺点：那些精准匹配的结果可能无法推送到最前面
- 实际例子，wiki，明显的most_field策略，搜索结果比较均匀，但是的确要翻好几页才能找到最匹配的结果 

##cross_field
> cross_field搜索，一个唯一的标识，跨了多个field，比如姓名，地址，姓名可以散落在first_name 和 last_name中，地址可以散落在country，province， city中。初步来说，如果要实现，可能使用most_field比较合适，因为best_field是优先搜素单个field最匹配的结果，cross_field本身不是一个field的问题  
###使用most_field实现cross_field的弊端
 - 只是找到尽可能多的field匹配的doc，而不是某个field完全匹配的doc
 - most_field没办法用minimum_should_match去掉长尾数据(匹配特别少的结果)
 - TF/IDF算法，比如Peter Smith 和Smith Williams,搜索Peter Smith的时候，由于first_name中很少有Smith的，所以query在所有的dicument中频率很低，得到的分数很高，可能Smith Williams反而会排在Peter Smith

###解决most_field策略下的cross_field弊端
- 使用copy_to将多个field组合成为一个field。问题出现在多个field，有了多个field以后，就会出现弊端。我们需要想办法将一个标识横跨多个field的情况，合并成一个field即可，比如说，一个人名，本来是first_name，last_name现在合并成为full_name
```
{
	"properties" : {
		"new_author_first_name":{
			"type":"text",
			"copy_to":"new_author_full_name"
		},
		"new_author_last_name":{
			"type":"text",
			"copy_to":"new_author_full_name"
		},
		"new_author_full_name":{
			"type":"text"
		}
	}
}
```

###原生crossFiled解决
```git exclude
{
	"query":{
		"multi_match":{
			"query":"Peter Smith",
			"type":"cross_fields",
			"operator":"and",
			"fields":["author_first_name", "author_last_name"]
		}
	}
}

```
- 问题1：只是找到尽可能多的field匹配的doc，而不是某个field完全匹配的doc --> 解决：要求每个term都必须在任何一个field中出现
  ```git exclude
Peter, Smith
要求Peter必须在author_first_name或者author_last_name中出现
要求Smith必须在author_first_name或者author_last_name中出现
```
- 问题二：most_field没办法使用minimum_should_match去掉长尾数据，就是匹配的特别少的结果 --> 解决:既然每个term都要求出现，长尾肯定被去掉了

- 问题三:TF/IDF算法，比如Peter Smith和Smith Williams，搜索Peter Smith的时候，由于firstName中很少有Smith，所以query在所有的document中频率很低，得到的分数很高，可能Smith Williams反而会排在Peter  Smith前面 --> 解决：极端IDF时候，将每个query在每个field中的IDF取出来，取最小值，就不会出现极端情况下的极大值

##phrase match
> 如果有两个需求
- java spark，就靠在一起，中间不能插入任何的其他字符，就要搜索这样的doc
- java spark，但是要求java和spark两个单词考的越近，doc的分数越高，排名就越靠前
要实现上述的两个需求，用match做全文检索，是搞不定的，必须使用**proximity match**近似匹配
phrase match, proximity match 短语匹配，近似匹配

###phrase match:将多个term作为一个短语，一起搜索，只有包含这个短语的doc才会作为返回结果，不像match，java spark， java， spark的doc都会返回
###term position
> doc中某个字段的中文字在该字段的第几位
```
```json
get _analyze
{"text": "hello, java spark", "analyzer":"standard"}
```
hell world, java spark
hi, spark java 
hello doc1(0)
world doc1(1)
java doc1(2) doc2(2)
spark doc1(3) doc2(1)
- 要找到每个term都在的一个共有的那些doc，就要求一个doc，必须包含每个term，才能拿出来进行计算
- doc1-->java和spark --> spark position恰巧比java大1 --> java的position是2，spark的position是3，满足条件
  doc2--> java和spark --> java position是2，spark position是1，spark position比java position小1，而不是大1 --> position 不满足条件
  
###slop
> slop:一个query string terms最多可以移动几次去尝试跟一个doc匹配上。slop搜索下，关键词离的越近，relevance score就越高。加了slop的phrase match 就是proximity match近似匹配

> match query 性能比phrase match和proximity match要高的多，因为后者都要计算position的距离，match query比phrase match的性能要高10倍，比proximity match的性能要高20倍。
但是因为es的性能基本都在毫秒级别，match query一般就在几毫秒或者几十毫秒，而phrase match和proximity match的性能在几十毫秒到几百毫秒之间，所以是可以接受的。  
优化proximity match的性能，一般就是减少要进行proximity match搜索的document的数量，主要的思路是用match query先过滤出需要的数据，然后在使用proximity match来根据term距离
提高doc的分数，同时proximity match只针对每个shard的分数排名在前n个doc起作用，来重新调整他们的分数，这个过程称为rescoring，累计分。因为一般用户分页查询，只会看到前几页的数据，索引不需要对所有的结果进行proximity match操作
**rescore：重打分**match到1000个doc，这时每一个doc都有一个分数，proximity match前50个doc，进行rescore重打分，即可，让前50个doc，term距离越近，排在越前面

##前缀搜索
 > prefix query不计算relevance score，与prefix filter唯一的区别就是filter会cache bitset。 前缀越短，要处理的doc越多，性能越差，尽可能使用长前缀搜索.
 
###通配符搜索
- ？ 任意字符
- * 0个或者任意多个字符

###正则
- [0-9]指定范围内的数字
- [a-z]指定范围的字母
- .一个字符
- +前面的正则表达式可以出现一次或者多次

##搜索推荐 search as you type
{
    "match_phrase_prefix":{
        "title":{
            "query":"hello w",
            "slop":"10",
            "max_expansions":50
        }
    }
}
原理与match_phrase类似，唯一的区别，就是把最后一个term作为前缀去搜索
hello就是去进行match，搜索对应的doc
w，会最作为前缀，会去扫描整个倒排索引，找到所有w开头的doc
然后找到所有的doc中，既包含hello，又包含w开头的字符的doc
根据slop计算，在slop范围内，能不能找到hello w，正好跟doc的hello和w开头的单词的position相匹配

指定的slop只有最后一个term会作为前缀。max_expansions指定prefix最多匹配多少个term，超过这个数量就不继续匹配了，限定性能。
比较耗性能

###ngram
> quick:5种长度下的ngram
- ngram length = 1, q u i c k
- ngram length = 2, qu ui ic ck
- ngram length = 3, qui uic ick
- ngram length = 4, quic uick 
- ngram length = 5, quick

####什么是edge ngram
> quick, anchor首字母进行ngram
q
qu
qui
quic
quick
使用edge ngram将每个单词都进行进一步的分词切分，用切分后的ngram来进行前缀搜索推荐功能
put /my_index
{
    "settings":{
        "analysis":{
            "filter": {
                "autocomplete_filter":{
                    "type":"edge ngram",
                    "min_gram":1,
                    "max_gram":20
                }
            },
            "analyzer":{
                "autocomplete":{
                    "type":"custom",
                    "tokenizer":"standard",
                    "filter":{
                        "lowercase",
                        "autocomplete_filter"
                    }
                }
            }
        }
    }
}

##TF/IDF 空间向量
###boolean model
> 类似and逻辑符，先过滤出包含指定term的doc
query "hello world" --> 过滤 --> hello / world / hello & world
bool --> must / must_not / should --> 过滤 --> 包含 / 不包含 / 可能包含
doc --> 不打分数 --> 正或反 true or false --> 为了减少后续要计算的doc数量，提升性能 

###TF/IDF
> 单个term在doc中的分数
query : "hello world"
doc1 : java is my favourite programming language, hello world！！！
doc2 : hello java, you are very good, oh hello world!!!
hello 对doc1评分
- TF : term frequency
    - 找到hello在doc1中出现了几次，会根据出现的次数给一个分数。一个term在一个doc中出现的次数越多，最后的相关度评分就会越高
- IDF : inversed document frequency
    - 找到hello在所有doc中出现的次数。一个term在所有doc中，出现的次数越多，那么最后的相关度评分就越低
- length norm
    - hello搜索的field的长度，field长度越长，相关度分数越低。
- 最后，将hello这个term对doc1的分数，综合TF、IDF、length norm得到一个综合评分

###vector space model
> 多个term对一个doc的总分数
query vector
doc vector，3个doc，一个包含一个term，一个包含另一个term，一个包含两个term
画在一个图中，取每个doc vector对query vector的弧度（夹角），给出每个doc对多个term的总分数。如果多个term，那么就是线性代数计算，无法使用图表示
弧度越小，分数越高

##lucene相关度分数算法



















##实战
```
PUT /car_shop
{
  "mappings": {
    "cars":{
      "properties":{
        "brand":{
          "type":"text",
          "analyzer":"ik_smart",
          "fields":{
            "raw":{
              "type":"keyword"
            }
          }
        },
        "name":{
          "type":"text",
          "analyzer":"ik_smart",
          "fields":{
            "raw":{
              "type":"keyword"
            }
          }
        }
      }
    }
  }
}
```
#聚合分析
##bucket  metric
- bucket:数据分组   类似group
- metric：对bucket执行某种聚合分析的操作，例如平均值、最大值、最小值

get /tvs/sales/_search
{
    {
        "size":0,
        "aggs":{
            "popular_color":{
                "terms":{
                    "field":"color"
                }
            }
        }
    }
}
size : 只获取聚合的结果，而不要执行聚合的原始数据
aggs ： 固定语法，要对一份数据进行聚合分析
popular_color : 对每个aggs起一个名字，这个名字可以是随意的
terms : 根据字段的值进行分组
field ： 根据指定的字段的值进行分组

##易并行聚合算法、三角选择原则，近似聚合算法
###易并行聚合算法
- 有些聚合分析的算法，很容易实现并行如max；有些聚合分析算法，是不容易实现并行的，比如count(distinct)，并不是在每个node上，直接就出一些distinct value，因为数据可能会很多。
- es采用近似聚合的方式，采用在每个node上进行近似估计的方式，得到最终的结论，count(distinct), 100w,1050w/95w -->5%左右的错误率。近似估计后的结果，不完全准确，但是速度会很快，一般会达到完全精准的算法的性能的数十倍。

###三角选择原则
> 精准 + 实时 + 大数据  ---> 选择2个
- 精准 + 实时：没有大数据，数据量很小，那么一般就是单机跑
- 精准 + 大数据：hadoop，批处理，非实时，可以处理海量的数据，保证精准，可能会跑几个小时
- 大数据 + 实时：es不精准，近似估计，可能会有百分之几的错误率

###近似聚合算法
- 如果采取近似估计的算法，延时在100ms左右，0.5%错误
- 如果采取100%精准的算法，延时一般在5s -- 几十s，甚至是几十分钟，几个小时0%错误率

###cardinality去重算法
> cardinality，count(distinct), 5%的算法错误率，性能在100ms左右
- precision_threshold优化准确率和内存开销
    - 设置unique value在多少以内，cardinality几乎保证100%准确
    - cardinality算法会占用precision_threshold * 8 byte内存消耗。如：100个brand将会占用100 * 8 = 800byte。
    - 占用内存很小，而且unique value如果的确在值以内，那么可以确保100%准确。当precision_threshold = 100时，数百万的unique_value，错误率在5%以内
    - precision_threshold值设置的越大，可以确保更多unique value场景下，100%的准确 
    
- HyperLogLog++(HLL) 算法性能优化
    - cardinality底层的算法是HLL，会对所有的unique value取hash值，通过hash值近似取求distinct，再求得count。默认情况下，发送一个cardinality请求的时候，会动态地对所有的field value取hash值，**可以将取hash值的操作，前移值建立索引的时候**
    ```
        put /tvs
        {
            "mappings":{
                "sales" :{
                    "properties":{
                        "brand":{
                            "type":"text",
                            "field":{
                                "hash":{
                                    "type":"murmur3"   // 一种取hash的算法
                                }
                            }
                        }
                    
                    }
                }
            }
        }
        
        get /tvs/sales/_search
        {
            "size":0,
            "aggs":{
                "distinctBrand":{
                    "cardinality":{
                        "field":"brand.hash",
                        "precision_threshold":100
                    }
                }
            }
        }
    ```
    
#网站访问量统计
##原始数据
```
put /website
{
    "mappings": {
        "logs": {
            "properties": {
                "latency": {
                    "type": "long"
                },
                "province": {
                    "type": "keyword"
                },
                "timestamp": {
                    "type": "date"
                }
            }
        }
    }
}


post /website/logs/_bulk
{"index":{}}
{"latency":105, "province":"江苏", "timestamp":"2018-10-28"}
{"index":{}}
{"latency":83, "province":"江苏", "timestamp":"2018-10-29"}
{"index":{}}
{"latency":92, "province":"江苏", "timestamp":"2018-10-29"}
{"index":{}}
{"latency":112, "province":"江苏", "timestamp":"2018-10-28"}
{"index":{}}
{"latency":68, "province":"江苏", "timestamp":"2018-10-28"}
{"index":{}}
{"latency":76, "province":"江苏", "timestamp":"2018-10-29"}
{"index":{}}
{"latency":101, "province":"新疆", "timestamp":"2018-10-28"}
{"index":{}}
{"latency":275, "province":"新疆", "timestamp":"2018-10-29"}
{"index":{}}
{"latency":116, "province":"新疆", "timestamp":"2018-10-29"}
{"index":{}}
{"latency":654, "province":"新疆", "timestamp":"2018-10-28"}
{"index":{}}
{"latency":389, "province":"新疆", "timestamp":"2018-10-28"}
{"index":{}}
{"latency":302, "province":"新疆", "timestamp":"2018-10-29"}

get /website/logs/_search
{
	"size":0,
	"aggs":{
		"group_by_province":{
			"terms":{
				"field":"province"
			},
			"aggs":{
				"top_province":{
					"top_hits":{        // 获取前几记录
						"_source":{
							"include":["province", "latency"]       // 设置显示字段
						},
						"size":3
					}
				}
			}
		}
	}
}



```
- 需求：比如一个网站，记录下每次请求的访问耗时，需要统计tp50, tp90,tp99
    - tp50:50%的请求的耗时最长在多少时间
    - tp90:90%的请求的耗时最长在多少时间
    - tp99:99%的请求的耗时最长在多少时间
    
##SLA
> 提供的服务标准。我们的网站的提供的访问延时的SLA，确保所有的请求100%，都必须在200ms以内，一般都要求在200ms以内。如果超过1s，则需要升级到A级故障，代表网站的访问性能和用户急剧下降
- 需求：
    - 在200ms以内的有百分之多少，在1000ms以内的有百分之多少。percentile_ranks metric
    - percentile_ranks 优化
        - TDigest算法：用很多节点来执行百分比的计算，近似估计，有误差，节点越多，越精准。
        - compression:限制节点的数量最多compression * 20,默认值100，越大，占用内存越多，越精准，性能越差，一个节点占用32byte，100*20*32=64kb
        
##基于doc_value正排索引分析聚合原理
- 倒排索引的弊端：使用倒排索引必须要遍历整个倒排索引。因为可能需要聚合的那个field的值，是分词的，比如hello world my name --> 一个doc的聚合field的值可能在倒排索引中对应多个value，所以，但你在倒排索引中找到一个值，发现它是属于某个doc的时候，还不能停，必须遍历完整个倒排索引，才能说确保找到了每个doc对应的所有terms。然后进行分组聚合
test     doc1
hello    doc2, doc3
world    doc2
...
- 正排索引：没有必须搜索完整的整个正排索引，比如有100w数据，搜索到15000次，就搜索完，就找到了1w个doc的聚合field的所有值，然后就可以执行分组聚合操作。
100w：
doc2:agg1 hello world
doc3:agg2 test hello
...
    
###doc value原理
（1）index-time生成
    put、post的时候，就会生成doc value数据，也就是正排索引。
（2）核心原理与倒排索引类似
    正排索引，也会写入磁盘文件中，然后呢os cache先进行缓存，以提升访问doc value正排索引的性能，如果os  cache内存大小不足够放得下整个正排索引，就会将doc value的数据写入磁盘文件中。
（3）性能问题：给jvm更少的内存，64g服务器，给jvm最多16g。
    es大量是基于os cache来进行缓存和提升性能的，不建议使用jvm内存来进行缓存，那样会导致一定的gc开销和oom问题。给jvm更少的内存，给os cache更大的内存。64g服务器给jvm最多16g，几十个g的内存给os cache。os cache可以提升doc value和倒排索引的缓存和查询效率
    
###column压缩
（1）所有值相同，直接保留单值
        doc1:550
        doc2:550
        doc3:500
        合并相同值，550，doc1和doc2都保留一个550的标识即可
（2）少于256个值，使用table encoding模式(一种压缩方式)
（3）大于256个值，看有没有最大公约数，有就除以最大公约数，然后保留这个最大公约数
        doc1:36
        doc2:24
        6 --> doc1:6,doc2:4---> 保留一个最大公约数6的标识，6也保存起来
（4）如果没有最大公约数，采取offset结合压缩的方式

###disable doc value
如果不需要doc value, 比如不进行聚合操作，那么可以禁用，减少磁盘空间的占用
```
put /my_index
{
    "mappings":{
        "my_type":{
            "properties":{
                "my_field":{
                    "type":"keyword",
                    "doc_value":false
                }
            }
        }
    }
}
```

###_string_field聚合分析以及fielddata原理
- 对于分词的field执行aggregation，发现报错

- 给分词的field，设置fielddata=true，发现可以执行 

- 使用内置的field不分词，对string field进行聚合

- 分词field+fielddata的工作原理
    > doc value --> 不分词的搜索field可以执行聚合操作--> 如果某个field不分词，那么在index-time，就会自动生成doc value --> 针对这些分词的field执行聚合的时候，自动就会用doc value来执行。。

分词field是没有doc value的，在index-time， 如果某个field是分词的，那么是不会给他建立doc value正排索引的，因为分词后，占用空间过大，所以默认是不支持分词field在进行聚合。因为分词field默认是没有doc value，所以直接对分词field执行聚合操作会报错

对于分词field，必须打开和使用fielddata，完全存在于纯内存中，结构和doc value类似，如果是ngram或者大量term，那么必将占用大量的内存

如果一定要对分词的field进行聚合，那么必须将fielddata=ture，然后es就会执行聚合操作的时候，现场将field对应的数据，建立一份fielddata正排索引，fielddata正排索引的结构跟doc value是类型的，但是只会将fielddata正排索引加载到内存中，然后基于内存中的fielddata正排索引执行分词field的聚合操作。

如果直接对分词field指定聚合，报错，才会提示开启fielddata=true，如果将fielddata uninverted index，正排索引，加载到内存，会耗费内存空间

为什么fielddata必须在内存？因为分词的字符串，需要按照term进行聚合，需要执行更加复杂的算法和操作，如果基于磁盘和os cache，那么性能会很差

##_fielddata内存控制及circuit breaker断路由器
###fielddata核心原理
    fielddata加载到内存的过程是lazy加载的，对一个analyzed field执行聚合操作是，才会加载，而且是field-level加载的。一个index的一个field，所有doc都会被加载，而不是少数doc。不是index-time创建，而是query-time创建
    
###fielddata内存机制
    indices.fielddata.cache.size : 20%，超出限制，清除内存已有fielddata数据.如果fielddata占用的内存超出这个比例的吸纳之，那么就清除内存中已有的fielddata数据
    默认无限制，吸纳之内存使用，但是会导致频繁evict和reload，大量io性能损耗，以及内存碎片和gc
    - 配置在elasticsearch.yml
    
    
###监控fieldata内存使用
   - get /_stats/fielddata?fields=*   es中所有fielddata内存占用情况
   - get /_nodes/stats/indices/fielddata?fields=*   es每个node中fielddata内存占用情况
   - get /_nodes/stats/indices/fielddata?level=indices&fields=*  es每个node中每个index下fielddata内存占用情况
   
###circuit breaker
    如果一次query load的fielddata超过总内存，就会oom。circuit breaker会估算query要加载的fielddata大小，如果超出总内存，就短路，query直接失败
  - indices.breaker.fielddata.limit:fielddata的内存限制，默认60%
  - indices.breaker.request.limit:执行聚合的内存限制，默认40%
  - indices.breaker.total.limit:综合上面两个，吸纳之在70%以内
  - 配置在elasticsearch.yml
  
###_fielddata filter的细粒度内存加载控制
```
put /my_index/_mapping/my_type
{
    "properties" :{
        "my_field":{
            "type":"text",
            "fielddata":{
                "filter":{
                    "frequency":{
                        "min":0.01,
                        "min_segement_size":500
                    }
                }
            }
        }
    }
}
```
min:仅仅加载至少在1%的doc中出现过的term对应的fielddata。比如某个值：hello，总共有1000个doc，hello必须在10个doc中出现，那么这个hello对应的fielddata才会加载到内存中
min_segment_size:少于500 doc的segment不加载fielddata。加载fielddata的时候，也是按照segment进行加载的，如果某个segmen里面的doc数量少于500个，那么这个segment就不加载

###_fielddata预加载机制以及序号标记预加载
1、field预加载
> 如果确实要对分词的fielddata执行聚合，那么每次都在query-time现场生产fielddata并加载到内存中，速度可能会比较慢，所以可以采用预加载的方式。
```
put /music/_mapping/_song
{
    "tags":{
        "type":"string",
        "fielddata":{
            "loading":"eager"  // query_time的fielddata生产和加载到内存，变为index-time，建立倒排索引的时候，会同步生成fielddata并且加载到内存中，这样对分词的聚合性能会大幅增强
        }
    }
}
```
2、序号标价预加载
global ordinal : 
    doc1:status1
    doc2:status2
    doc3:status2
    doc4:status1
    有很多重复值的情况，会进行global ordinal标记
    status1 --> 0
    status2 --> 1
    doc1:0
    doc2:1
    doc3:1
    doc4:0
    建立这样的fielddata会减少重复字符串的出现次数，减少内存的消耗。
```
put /music/_mapping/_song
{
   "properties":{
        "song_title":{
               "type":"string",
               "fielddata":{
                   "loading":"eager_global_ordinals"
               }
        }
    }
}
```

##海量bucket优化机制：从深度优先到广度优先
{
    "aggs":{
        "actors":{
            "terms":{
                "field":"actors",
                "size":10,
                "collect_mode":"breadth_first"  // 广度优先
            }
        },
        "aggs":{
            "costars":{
                "terms":{
                    "field":"films",
                    "size":5
                }
            }
        }
    }
}
> 深度优先的方式去执行聚合操作
       actor1                actor2         ...      actor
film1 film2  film3     film1 film2 film3          film1 film2  film3
假设有10w个actor，最后其实是主要10个actor就可以了。但是已经建立了深度优先的方式，构建了一棵完整的树出来，10w个actor，每个actor平均有10部电影，10w + 100w --> 110w的数据量的一棵树，裁剪掉10w个actor中的99999个，剩下10个actor，每个actor的10个film裁掉5个。构建了大量的数据，然后裁减掉99.9%的数据，比较浪费。

> 改用广度优先的方式去执行聚合
actor1          actor2          ...         actor
10w个actor，不去构建它下面的film 数据，10w ---> 99990个actor裁剪掉，剩下10个actor，构建film，裁剪其中的5个film即可，10w-->50

#数据建模
##文件系统建模及文件搜索
```
put /fs
{
    "settings":{
        "analysis":{
            "analyzer":{
                "paths":{
                    "tokenizer":"path_hierarchy"
                }
            }
        }
    }
    
    get /fs/_analyze
    {
        "text":"/a/b/c/d",
        "analyzer":"paths"
    }

}

put /fs/_mapping/file
{
	"properties":{
		"name":{
			"type":"keyword"
		},
		"path":{
			"type":"keyword",
			"fields":{
				"tree":{
					"type":"text",
					"analyzer":"paths"
				}
			}
		}
	}
}


put /fs/file/1
{
    "name":"README.TXT",
    "path":"/var/study/es/elasticseach-demo",
    "content":"elasticsearch学习demo，欢迎阅读！"
}

get /fs/file/_search
{
    "query": {
        "bool": {
            "must": [
                {
                    "match": {
                        "content": "elasticsearch"
                    }
                },
                {
                    "constant_score": {
                        "filter": {
                            "term": {
                                "path": "/elasticseach-demo"
                            }
                        }
                    }
                }
            ]
        }
    }
}

// 搜索指定目录下的文件
get /fs/file/_search
{
    "query": {
        "bool": {
            "must": [
                {
                    "match": {
                        "content": "elasticsearch"
                    }
                },
                {
                    "constant_score": {
                        "filter": {
                            "term": {
                                "path.tree": "/var/study/es"    //  "path.tree": "/var/study/es/" 错误 
                            }
                        }
                    }
                }
            ]
        }
    }
}
```

#全局锁实现悲观锁并发控制
##全局锁
   - 测试目录：/var/study/es。如果多个线程过来，要并发地给/var/study/es/elasticsearch-demo下的README.md修改文件名。需要进行并发控制，避免出现多线程的并发安全问题，比如多个线程修改，纯并发，先执行的修改操作被后执行的修改操作给覆盖了。
    需要先获取到版本号，修改时携带version，如果修改时的version和获取的version不同，则需要重新获取version，尝试再次修改。
   - 全局锁：直接锁掉整个fs index
```
PUT /fs/lock/global/_create
{}
- fs :需要上锁的index
- lock：需要上锁的type
- global：上的全局锁对应的这个doc的id
- _create:前置必须是创建，如果/fs/lock/global这个doc已经存在，那么创建失败，报错
- 例：
> 
    put /fs/file/global/_create
    {}
    response:
    {
      "_index": "fs",
      "_type": "file",
      "_id": "global",
      "_version": 1,
      "result": "created",
      "_shards": {
          "total": 2,
          "successful": 1,
          "failed": 0
      },
      "_seq_no": 0,
      "_primary_term": 1
  }
  
  另一个线程尝试对已经上锁的type上锁
  {
      "error": {
          "root_cause": [
              {
                  "type": "version_conflict_engine_exception",
                  "reason": "[file][global]: version conflict, document already exists (current version [1])",
                  "index_uuid": "74omk64JQQKrIXFN8YWTVw",
                  "shard": "2",
                  "index": "fs"
              }
          ],
          "type": "version_conflict_engine_exception",
          "reason": "[file][global]: version conflict, document already exists (current version [1])",
          "index_uuid": "74omk64JQQKrIXFN8YWTVw",
          "shard": "2",
          "index": "fs"
      },
      "status": 409
  }

post /fs/file/1
{
	"doc":{
		"name":"readme.md"
	}
}
{
    "_index": "fs",
    "_type": "file",
    "_id": "1",
    "_version": 3,
    "result": "updated",
    "_shards": {
        "total": 2,
        "successful": 1,
        "failed": 0
    },
    "_seq_no": 5,
    "_primary_term": 1
}

如果失败，再次尝试上锁，执行各种操作

DELETE /fs/lock/global

```


























