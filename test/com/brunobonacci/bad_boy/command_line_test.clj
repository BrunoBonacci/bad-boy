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

 (parse-options "prefix-*")   => {:targets [{:target-name "prefix-*"}]}

 )



(facts
 "targets: by tag"

 (parse-options "tag:Chaos-testing=opt-in") => {:targets [{:tag {"Chaos-testing" "opt-in"}}]}
 (parse-options "tag:SomeTagName='some value'") => {:targets [{:tag {"SomeTagName" "some value"}}]}

 )



(facts
 "targets: by presets"

 (parse-options "--default-selection") => {:targets [{:preset :default-selection}]}

 )


(facts
 "multiple targets:"

 (parse-options "--default-selection very unlucky* people tag:application=web-server")
 => {:targets
    [{:preset :default-selection}
     {:target-name "very"}
     {:target-name "unlucky*"}
     {:target-name "people"}
     {:tag {"application" "web-server"}}]}

 )


(facts
 "killer-run"

 (parse-options "--killer-run all") => {:killer-run :all}
 (parse-options "--killer-run :all") => {:killer-run :all}
 (parse-options "--killer-run ::all") => {:killer-run :all}
 (parse-options "--killer-run something-else") => {:killer-run :something-else}
 (parse-options "--killer-run") => {:killer-run :all}

 )
