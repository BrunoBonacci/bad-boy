# bad-boy

Chaos testing and infrastructure hardening tool.

## Usage

![bad boy](./doc/badboy.png)

Bad stuff in progress, more to come...!


  - for production environments
```
# 30% of chance to be killed on a daily basis.
1cfg -k bad-boy -e prd -v 0.5.0 -t edn SET '{:groups {:all {:attack-rate [0.30 :daily]}}}'
```


  - for event logging data
```
# 30% of chance to be killed on a daily basis.
1cfg -k bad-boy -e prd -v 0.5.0 -t edn SET '
{:groups {:all {:attack-rate [0.30 :daily]}}
 ;; send log events to the following destinations
 :mulog
 {:type :multi
  :publishers
  [{:type :console       :pretty?    true}
   {:type :cloudwatch    :group-name "mulog"}
   {:type :elasticsearch :url        "http://localhost:9200/"}]}
 }'
```


  - for bad-boy development
```
# For delevelopment with 100% probability to kill
1cfg -b fs -k bad-boy -e local -v 0.5.0 -t edn SET '{:groups {:all {:attack-rate [1 :minute]}}}'

export DRY_RUN=1
lein run
```

## License

Copyright © 2019-2020 Bruno Bonacci - Distributed under the [Apache License v2.0](http://www.apache.org/licenses/LICENSE-2.0)
