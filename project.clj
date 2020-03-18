(defproject com.brunobonacci/bad-boy (-> "./resources/bad-boy.version" slurp .trim)
  :description "A chaos testing and infrastructure hardening tool."

  :url "https://github.com/BrunoBonacci/bad-boy"

  :license {:name "Apache License 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}

  :scm {:name "git" :url "https://github.com/BrunoBonacci/bad-boy.git"}

  :dependencies [[org.clojure/clojure           "1.10.1"]
                 [org.clojure/tools.logging     "1.0.0"]
                 [com.cognitect.aws/api         "0.8.445"]
                 [com.cognitect.aws/endpoints   "1.1.11.732"]
                 [com.cognitect.aws/ec2         "793.2.627.0"]
                 [com.cognitect.aws/autoscaling "792.2.622.0"]
                 [instaparse                    "1.4.10"]
                 [com.brunobonacci/where        "0.5.5"]
                 [com.brunobonacci/safely       "0.5.0"]
                 [com.brunobonacci/mulog        "0.1.8"]]

  :global-vars {*warn-on-reflection* true}

  :jvm-opts ["-server"]

  :bin {:name "bad-boy"
        :jvm-opts ["-server" "$JVM_OPTS" "-Dfile.encoding=utf-8"]}

  :main com.brunobonacci.bad-boy.main

  :profiles {:uberjar
             {:aot :all
              ;; temp fix for logging
              :dependencies [[ch.qos.logback/logback-classic "1.2.3"]
                             [org.codehaus.janino/janino "3.1.1"]
                             [com.brunobonacci/mulog-elasticsearch "0.1.8"]
                             [com.internetitem/logback-elasticsearch-appender "1.6"
                              :exclusions [com.fasterxml.jackson.core/jackson-core]]
                             [clj-time "0.15.2"]]
              :resource-paths ["dev-resources"]}

             :dev
             {:dependencies [[midje "1.9.9"]
                             [org.clojure/test.check "1.0.0"]
                             [criterium "0.4.5"]
                             [ch.qos.logback/logback-classic "1.2.3"]]
              :resource-paths ["dev-resources"]

              :plugins      [[lein-midje "3.2.1"]
                             [lein-binplus "0.6.5"]]}}

  )
