(ns com.brunobonacci.bad-boy.command-line-test
  (:require [com.brunobonacci.bad-boy.command-line :refer :all]
            [midje.sweet :refer :all]))


(facts
 "empty command line"

 (parse-options "") => {}

 )


(facts
 "global options"

 (parse-options "-h")        => {:help true}
 (parse-options "--help")    => {:help true}
 (parse-options "-v")        => {:version true}
 (parse-options "--version") => {:version true}
 (parse-options "--dry-run") => {:dry-run true}
 (parse-options "--dryrun")  => {:dry-run true}

 )



(facts
 "targets: by name"

 (parse-options "prefix-*")   => {:target {:target-name "prefix-*"}}

 )



(facts
 "targets: by tag"

 (parse-options "tag:Chaos-testing=opt-in") => {:target {:tag {"Chaos-testing" "opt-in"}}}
 (parse-options "tag:SomeTagName='some value'") => {:target {:tag {"SomeTagName" "some value"}}}

 )
