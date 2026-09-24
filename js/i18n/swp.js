/* LORD SAI — Hindi / Marathi phrases for swp.html (+ js/swp-case-studies.js chart labels). Format: see js/i18n/common.js */
(function (w) {
    var P = w.LSI_Phrases = w.LSI_Phrases || {};
    var add = function (o) { for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) P[k] = o[k]; };

    /* Dates written by the case-study charts and tables: "15 May 2005", "May 2005",
       and the chart's "Lowest ₹x L · Apr 2020" marker. */
    var MONTHS = [
        ["Jan", "जनवरी", "जानेवारी"], ["Feb", "फरवरी", "फेब्रुवारी"], ["Mar", "मार्च", "मार्च"],
        ["Apr", "अप्रैल", "एप्रिल"], ["May", "मई", "मे"], ["Jun", "जून", "जून"],
        ["Jul", "जुलाई", "जुलै"], ["Aug", "अगस्त", "ऑगस्ट"], ["Sep", "सितंबर", "सप्टेंबर"],
        ["Oct", "अक्टूबर", "ऑक्टोबर"], ["Nov", "नवंबर", "नोव्हेंबर"], ["Dec", "दिसंबर", "डिसेंबर"]
    ];
    MONTHS.forEach(function (m) {
        P["{#} " + m[0] + " {#}"] = ["{#} " + m[1] + " {#}", "{#} " + m[2] + " {#}"];
        P[m[0] + " {#}"] = [m[1] + " {#}", m[2] + " {#}"];
        P["Lowest ₹{#} L · " + m[0] + " {#}"] = ["न्यूनतम ₹{#} लाख · " + m[1] + " {#}", "किमान ₹{#} लाख · " + m[2] + " {#}"];
        P["Lowest ₹{#} Cr · " + m[0] + " {#}"] = ["न्यूनतम ₹{#} करोड़ · " + m[1] + " {#}", "किमान ₹{#} कोटी · " + m[2] + " {#}"];
    });

    add({
        "Mutual Fund Distribution (ARN-{#}) | SWP — Systematic Withdrawal Plan | Lord Sai Investment (ARN-{#})": [
            "म्यूचुअल फंड वितरण (ARN-{#}) | SWP — सिस्टमैटिक विथड्रॉल प्लान | लॉर्ड साई इन्वेस्टमेंट (ARN-{#})",
            "म्युच्युअल फंड वितरण (ARN-{#}) | SWP — सिस्टिमॅटिक विथड्रॉल प्लॅन | लॉर्ड साई इन्व्हेस्टमेंट (ARN-{#})"],
        "SWP — Systematic Withdrawal Plan | Lord Sai Investment (ARN-{#})": [
            "SWP — सिस्टमैटिक विथड्रॉल प्लान | लॉर्ड साई इन्वेस्टमेंट (ARN-{#})",
            "SWP — सिस्टिमॅटिक विथड्रॉल प्लॅन | लॉर्ड साई इन्व्हेस्टमेंट (ARN-{#})"],
        "What is SWP": ["SWP क्या है", "SWP म्हणजे काय"],
        "Ideal Use Cases": ["आदर्श उपयोग", "योग्य उपयोग"],
        "SWP vs FD & Dividends": ["SWP बनाम FD और डिविडेंड", "SWP विरुद्ध FD आणि लाभांश"],
        "How SWP Works": ["SWP कैसे काम करता है", "SWP कसे काम करते"],
        "{#} Key Benefits": ["{#} प्रमुख लाभ", "{#} प्रमुख फायदे"],
        "Corpus Management": ["कॉर्पस प्रबंधन", "कॉर्पस व्यवस्थापन"],
        "Longevity Scenarios": ["दीर्घायु परिदृश्य", "दीर्घायुष्य परिस्थिती"],
        "Real Case Studies": ["वास्तविक केस स्टडी", "प्रत्यक्ष केस स्टडी"],
        "SWP FAQ": ["SWP अक्सर पूछे जाने वाले प्रश्न", "SWP वारंवार विचारले जाणारे प्रश्न"],
        "Go to SIP Page": ["SIP पेज पर जाएं", "SIP पानावर जा"],
        "Predictable Cash-Flow Planning": ["निश्चित कैश-फ्लो नियोजन", "निश्चित कॅश-फ्लो नियोजन"],
        "Set Up Your Systematic Withdrawal Plan (SWP)": ["अपना सिस्टमैटिक विथड्रॉल प्लान (SWP) शुरू करें", "तुमचा सिस्टिमॅटिक विथड्रॉल प्लॅन (SWP) सुरू करा"],
        "Create a customized monthly payout stream tailored to your retirement goals, recurring expenses, and liquidity requirements.": [
            "अपने सेवानिवृत्ति लक्ष्यों, नियमित खर्चों और नकदी की जरूरतों के अनुसार एक विशेष मासिक भुगतान व्यवस्था बनाएं।",
            "तुमची सेवानिवृत्तीची उद्दिष्टे, नियमित खर्च आणि रोख गरजांनुसार खास मासिक रक्कम मिळण्याची व्यवस्था तयार करा."],
        "REGULAR CASH-FLOW": ["नियमित कैश-फ्लो", "नियमित कॅश-फ्लो"],
        "<0/><1>Higher Tax Efficiency:</1> In FDs, {#}% of interest is taxable at your income slab. In SWP, redemption is a mix of capital and gains, so tax applies <2>only to the capital gain portion</2>.": [
            "<0/><1>अधिक कर-कुशलता:</1> FD में {#}% ब्याज आपके आय स्लैब के अनुसार कर योग्य होता है। SWP में रिडेम्पशन पूंजी और लाभ का मिश्रण होता है, इसलिए कर <2>केवल पूंजीगत लाभ वाले हिस्से पर</2> लगता है।",
            "<0/><1>अधिक कर-कार्यक्षमता:</1> FD मध्ये {#}% व्याज तुमच्या उत्पन्न स्लॅबनुसार करपात्र असते. SWP मध्ये रिडेम्पशन हे भांडवल आणि नफ्याचे मिश्रण असते, त्यामुळे कर <2>फक्त भांडवली नफ्याच्या भागावर</2> लागतो."],
        "<0/><1>Inflation Defense:</1> Fixed deposits lose purchasing power over time, whereas mutual fund hybrid/equity assets can potentially outpace inflation.": [
            "<0/><1>महंगाई से बचाव:</1> फिक्स्ड डिपॉजिट समय के साथ क्रय शक्ति खो देते हैं, जबकि म्यूचुअल फंड की हाइब्रिड/इक्विटी संपत्तियां महंगाई से आगे निकल सकती हैं।",
            "<0/><1>महागाईपासून बचाव:</1> मुदत ठेवींची क्रयशक्ती काळानुसार कमी होते, तर म्युच्युअल फंडांच्या हायब्रिड/इक्विटी मालमत्ता महागाईपेक्षा पुढे जाऊ शकतात."],
        "Real Statements, Not Promises": ["असली स्टेटमेंट, वादे नहीं", "खरी स्टेटमेंट्स, आश्वासने नव्हे"],
        "See Real SWP Results Before You Decide": ["फैसला करने से पहले SWP के असली नतीजे देखें", "निर्णय घेण्यापूर्वी SWP चे खरे निकाल पाहा"],
        "Three original statements from fund houses showing exactly what happened, month by month, when investors drew a regular income from their corpus. Free to open, no sign-up needed.": [
            "फंड हाउस के तीन मूल स्टेटमेंट, जो महीने-दर-महीने दिखाते हैं कि निवेशकों के अपने कॉर्पस से नियमित आय लेने पर असल में क्या हुआ। खोलना मुफ्त है, साइन-अप की जरूरत नहीं।",
            "फंड हाऊसेसची तीन मूळ स्टेटमेंट्स, जी दाखवतात की गुंतवणूकदारांनी त्यांच्या कॉर्पसमधून नियमित उत्पन्न घेतल्यावर महिन्यागणिक प्रत्यक्षात काय झाले. उघडणे मोफत, साइन-अपची गरज नाही."],
        "Never fell below ₹{#} Cr": ["कभी ₹{#} करोड़ से नीचे नहीं गया", "कधीही ₹{#} कोटींच्या खाली गेला नाही"],
        "still invested after paying out ₹{#} lakh": ["₹{#} लाख का भुगतान करने के बाद भी निवेशित", "₹{#} लाख दिल्यानंतरही गुंतवलेले"],
        "₹{#} Crore That Paid ₹{#} Every Month for {#} Years": ["₹{#1} करोड़, जिसने {#3} वर्षों तक हर महीने ₹{#2} दिए", "₹{#1} कोटी, ज्यांनी {#3} वर्षे दरमहा ₹{#2} दिले"],
        "A steady monthly income from {#} to {#}, and the corpus still doubled. See every one of the {#} payouts.": [
            "{#} से {#} तक स्थिर मासिक आय, और फिर भी कॉर्पस दोगुना हो गया। सभी {#} भुगतान देखें।",
            "{#} ते {#} पर्यंत स्थिर मासिक उत्पन्न, आणि तरीही कॉर्पस दुप्पट झाला. सर्व {#} रकमा पाहा."],
        "SBI Equity Hybrid Fund · {#}-page statement": ["SBI Equity Hybrid Fund · {#} पृष्ठों का स्टेटमेंट", "SBI Equity Hybrid Fund · {#} पानांचे स्टेटमेंट"],
        "Open SBI SWP Story": ["SBI SWP की कहानी खोलें", "SBI SWP ची कथा उघडा"],
        "or explore the interactive chart": ["या इंटरैक्टिव चार्ट देखें", "किंवा इंटरॲक्टिव्ह चार्ट पाहा"],
        "{#}-year track record": ["{#} वर्षों का ट्रैक रिकॉर्ड", "{#} वर्षांचा ट्रॅक रेकॉर्ड"],
        "left from ₹{#} lakh, after paying out ₹{#} lakh": ["₹{#} लाख में से, ₹{#} लाख का भुगतान करने के बाद शेष", "₹{#} लाखांपैकी, ₹{#} लाख दिल्यानंतर शिल्लक"],
        "{#} Years of Monthly Income, Through Every Crash": ["हर गिरावट के बीच {#} वर्षों की मासिक आय", "प्रत्येक घसरणीतून {#} वर्षांचे मासिक उत्पन्न"],
        "₹{#} every month for {#} months since {#}, through the dot-com bust, {#} and COVID. The longest SWP journey we have.": [
            "{#3} से {#2} महीनों तक हर महीने ₹{#1}, डॉट-कॉम संकट, {#4} और कोविड के दौर में भी। हमारे पास उपलब्ध सबसे लंबी SWP यात्रा।",
            "{#3} पासून {#2} महिने दरमहा ₹{#1}, डॉट-कॉम संकट, {#4} आणि कोविडच्या काळातही. आमच्याकडील सर्वात दीर्घ SWP प्रवास."],
        "DSP Aggressive Hybrid Fund · {#}-page report": ["DSP Aggressive Hybrid Fund · {#} पृष्ठों की रिपोर्ट", "DSP Aggressive Hybrid Fund · {#} पानांचा अहवाल"],
        "Open DSP {#}-Year Journey": ["DSP की {#}-वर्षीय यात्रा खोलें", "DSP चा {#} वर्षांचा प्रवास उघडा"],
        "Includes tax breakdown": ["टैक्स का विवरण शामिल", "कराचा तपशील समाविष्ट"],
        "₹{#} L tax": ["₹{#} लाख टैक्स", "₹{#} लाख कर"],
        "on ₹{#} lakh of withdrawals, as per the illustration": ["उदाहरण के अनुसार ₹{#} लाख की निकासी पर", "उदाहरणानुसार ₹{#} लाख काढलेल्या रकमेवर"],
        "₹{#} Lakh of Income, Tax Worked Out Month by Month": ["₹{#} लाख की आय, महीने-दर-महीने टैक्स की गणना", "₹{#} लाखांचे उत्पन्न, महिन्यागणिक कराची गणना"],
        "See how only the gain part of each withdrawal is taxed, and how the plan came through the {#} crash with ₹{#} Cr left.": [
            "देखें कि हर निकासी में केवल लाभ वाले हिस्से पर ही टैक्स कैसे लगता है, और {#} की गिरावट के बाद भी योजना में ₹{#} करोड़ कैसे बचे रहे।",
            "प्रत्येक रकमेतील फक्त नफ्याच्या भागावरच कर कसा लागतो आणि {#} च्या घसरणीनंतरही योजनेत ₹{#} कोटी कसे शिल्लक राहिले ते पाहा."],
        "Bandhan Aggressive Hybrid Fund · {#}-page illustration": ["Bandhan Aggressive Hybrid Fund · {#} पृष्ठों का उदाहरण", "Bandhan Aggressive Hybrid Fund · {#} पानांचे उदाहरण"],
        "Open Bandhan Tax Breakdown": ["Bandhan का टैक्स विवरण खोलें", "Bandhan चा कर तपशील उघडा"],
        "Illustrations published by the respective fund houses using historical NAVs, shared for investor education only; not a recommendation of any scheme. Past performance may or may not be sustained in future. Mutual Fund investments are subject to market risks, read all scheme related documents carefully.": [
            "संबंधित फंड हाउस द्वारा ऐतिहासिक NAV के आधार पर प्रकाशित उदाहरण, केवल निवेशक शिक्षा के लिए साझा किए गए हैं; यह किसी योजना की सिफारिश नहीं है। पिछला प्रदर्शन भविष्य में बना रह भी सकता है और नहीं भी। म्यूचुअल फंड निवेश बाजार जोखिमों के अधीन हैं, योजना से संबंधित सभी दस्तावेज ध्यान से पढ़ें।",
            "संबंधित फंड हाऊसेसनी ऐतिहासिक NAV वापरून प्रकाशित केलेली उदाहरणे, केवळ गुंतवणूकदार शिक्षणासाठी दिली आहेत; ही कोणत्याही योजनेची शिफारस नाही. मागील कामगिरी भविष्यात टिकेलच असे नाही. म्युच्युअल फंड गुंतवणूक बाजारातील जोखमींच्या अधीन असते, योजनेशी संबंधित सर्व कागदपत्रे काळजीपूर्वक वाचा."],
        "Financial Planning Principles": ["वित्तीय नियोजन के सिद्धांत", "आर्थिक नियोजनाची तत्त्वे"],
        "Corpus Longevity & Prudent Withdrawal Rules": ["कॉर्पस की दीर्घायु और विवेकपूर्ण निकासी के नियम", "कॉर्पसचे दीर्घायुष्य आणि विवेकी पैसे काढण्याचे नियम"],
        "To ensure your accumulated capital sustains your entire retirement or cash-flow horizon, follow these three golden rules:": [
            "आपकी जमा पूंजी पूरी सेवानिवृत्ति या कैश-फ्लो अवधि तक चले, इसके लिए इन तीन सुनहरे नियमों का पालन करें:",
            "तुमचे जमा भांडवल संपूर्ण सेवानिवृत्ती किंवा कॅश-फ्लो कालावधीपर्यंत टिकावे यासाठी हे तीन सुवर्ण नियम पाळा:"],
        "Prudent Withdrawal Rate": ["विवेकपूर्ण निकासी दर", "विवेकी पैसे काढण्याचा दर"],
        "Aim to withdraw between <0>{#}% to {#}% per annum</0> of your initial corpus. When withdrawal is lower than the expected long-term compounding return, the remaining corpus keeps growing.": [
            "अपने शुरुआती कॉर्पस का <0>{#}% से {#}% प्रति वर्ष</0> निकालने का लक्ष्य रखें। जब निकासी अपेक्षित दीर्घकालिक चक्रवृद्धि रिटर्न से कम होती है, तो शेष कॉर्पस बढ़ता रहता है।",
            "तुमच्या सुरुवातीच्या कॉर्पसपैकी <0>दरवर्षी {#}% ते {#}%</0> रक्कम काढण्याचे लक्ष्य ठेवा. काढलेली रक्कम अपेक्षित दीर्घकालीन चक्रवाढ परताव्यापेक्षा कमी असल्यास उर्वरित कॉर्पस वाढत राहतो."],
        "Hybrid Asset Allocation": ["हाइब्रिड एसेट एलोकेशन", "हायब्रिड ॲसेट ॲलोकेशन"],
        "Opt for Balanced Advantage or Multi-Asset Allocation funds for your SWP. These funds automatically rebalance between equity and debt, dampening downside during market crashes.": [
            "अपने SWP के लिए बैलेंस्ड एडवांटेज या मल्टी-एसेट एलोकेशन फंड चुनें। ये फंड इक्विटी और डेट के बीच अपने आप संतुलन बनाते हैं, जिससे बाजार गिरने पर नुकसान कम होता है।",
            "तुमच्या SWP साठी बॅलन्स्ड ॲडव्हान्टेज किंवा मल्टि-ॲसेट ॲलोकेशन फंड निवडा. हे फंड इक्विटी आणि डेट यांच्यात आपोआप संतुलन साधतात, त्यामुळे बाजार कोसळल्यावर नुकसान कमी होते."],
        "Cash Buffer Strategy": ["नकद बफर रणनीति", "रोख राखीव रणनीती"],
        "Maintain {#} to {#} months of living expenses in a liquid fund. In the rare event of a severe prolonged bear market, cash can be drawn from the buffer without selling equity units.": [
            "{#} से {#} महीनों का जीवन-यापन खर्च लिक्विड फंड में रखें। लंबी और गहरी मंदी की दुर्लभ स्थिति में इक्विटी यूनिट्स बेचे बिना इस बफर से पैसा निकाला जा सकता है।",
            "{#} ते {#} महिन्यांचा राहणीमान खर्च लिक्विड फंडात ठेवा. दीर्घ आणि तीव्र मंदीच्या दुर्मीळ परिस्थितीत इक्विटी युनिट्स न विकता या राखीव निधीतून पैसे काढता येतात."],
        "The secret to a long-lasting SWP is keeping the annual withdrawal rate below or near the fund's conservative expected compounding rate. Here is how two different withdrawal rates perform over {#}-year and {#}-year horizons, on an <0>assumed corpus of ₹{#} Lakh</0> growing at an <1>assumed return of {#}% p.a.</1>:": [
            "लंबे समय तक चलने वाले SWP का रहस्य है वार्षिक निकासी दर को फंड की रूढ़िवादी अपेक्षित चक्रवृद्धि दर से कम या उसके आसपास रखना। देखें कि <1>{#4}% प्रति वर्ष के अनुमानित रिटर्न</1> से बढ़ते <0>₹{#3} लाख के अनुमानित कॉर्पस</0> पर दो अलग-अलग निकासी दरें {#1} और {#2} वर्षों में कैसा प्रदर्शन करती हैं:",
            "दीर्घकाळ टिकणाऱ्या SWP चे रहस्य म्हणजे वार्षिक पैसे काढण्याचा दर फंडाच्या सावध अपेक्षित चक्रवाढ दराच्या खाली किंवा जवळपास ठेवणे. <1>{#4}% वार्षिक गृहीत परताव्याने</1> वाढणाऱ्या <0>₹{#3} लाखांच्या गृहीत कॉर्पसवर</0> दोन वेगवेगळे पैसे काढण्याचे दर {#1} आणि {#2} वर्षांत कशी कामगिरी करतात ते पाहा:"],
        "Real Fund History": ["वास्तविक फंड इतिहास", "प्रत्यक्ष फंड इतिहास"],
        "Real SWP Journeys: Three Case Studies": ["वास्तविक SWP यात्राएं: तीन केस स्टडी", "प्रत्यक्ष SWP प्रवास: तीन केस स्टडी"],
        "The table above assumes a smooth {#}% every year. Real markets never move that smoothly. Below are three SWP illustrations published by the fund houses themselves, calculated on each fund's <0>actual historical NAVs</0>, through the {#}–{#} dot-com crash, the {#} crisis and the March {#} COVID fall. Pick a case to see how the money behaved month by month.": [
            "ऊपर की तालिका हर साल एक समान {#1}% मानती है। असली बाजार कभी इतनी सहजता से नहीं चलते। नीचे फंड हाउस द्वारा खुद प्रकाशित तीन SWP उदाहरण हैं, जिनकी गणना हर फंड के <0>वास्तविक ऐतिहासिक NAV</0> पर की गई है — {#2}–{#3} के डॉट-कॉम संकट, {#4} के संकट और मार्च {#5} की कोविड गिरावट के दौर में। महीने-दर-महीने पैसे का व्यवहार देखने के लिए कोई केस चुनें।",
            "वरील तक्ता दरवर्षी एकसमान {#1}% गृहीत धरतो. प्रत्यक्ष बाजार कधीच इतक्या सहजपणे चालत नाहीत. खाली फंड हाऊसेसनी स्वतः प्रकाशित केलेली तीन SWP उदाहरणे आहेत, जी प्रत्येक फंडाच्या <0>प्रत्यक्ष ऐतिहासिक NAV</0> वर मोजली आहेत — {#2}–{#3} चे डॉट-कॉम संकट, {#4} चे संकट आणि मार्च {#5} मधील कोविड घसरण यांतून. महिन्यागणिक पैशांचे वर्तन पाहण्यासाठी एखादी केस निवडा."],
        "Original statements:": ["मूल स्टेटमेंट:", "मूळ स्टेटमेंट्स:"],
        "₹{#} Cr · ₹{#} a month ({#}%) · {#} years": ["₹{#} करोड़ · ₹{#} प्रति माह ({#}%) · {#} वर्ष", "₹{#} कोटी · दरमहा ₹{#} ({#}%) · {#} वर्षे"],
        "₹{#} L · ₹{#} a month ({#}%) · {#} years": ["₹{#} लाख · ₹{#} प्रति माह ({#}%) · {#} वर्ष", "₹{#} लाख · दरमहा ₹{#} ({#}%) · {#} वर्षे"],
        "Corpus value vs money withdrawn": ["कॉर्पस मूल्य बनाम निकाली गई राशि", "कॉर्पस मूल्य विरुद्ध काढलेली रक्कम"],
        "SBI Equity Hybrid Fund (Regular Growth), May {#} – May {#}": ["SBI Equity Hybrid Fund (रेगुलर ग्रोथ), मई {#} – मई {#}", "SBI Equity Hybrid Fund (रेग्युलर ग्रोथ), मे {#} – मे {#}"],
        "Whole journey": ["पूरी यात्रा", "संपूर्ण प्रवास"],
        "First {#} years": ["पहले {#} वर्ष", "पहिली {#} वर्षे"],
        "Corpus value": ["कॉर्पस मूल्य", "कॉर्पस मूल्य"],
        "Total withdrawn so far": ["अब तक कुल निकासी", "आतापर्यंत काढलेली एकूण रक्कम"],
        "Amount invested (₹{#} Cr)": ["निवेशित राशि (₹{#} करोड़)", "गुंतवलेली रक्कम (₹{#} कोटी)"],
        "Amount invested (₹{#} L)": ["निवेशित राशि (₹{#} लाख)", "गुंतवलेली रक्कम (₹{#} लाख)"],
        "₹{#} L withdrawn": ["₹{#} लाख निकाले गए", "₹{#} लाख काढले"],
        "₹{#} Cr withdrawn": ["₹{#} करोड़ निकाले गए", "₹{#} कोटी काढले"],
        "Lowest ₹{#} L": ["न्यूनतम ₹{#} लाख", "किमान ₹{#} लाख"],
        "Lowest ₹{#} Cr": ["न्यूनतम ₹{#} करोड़", "किमान ₹{#} कोटी"],
        "corpus value": ["कॉर्पस मूल्य", "कॉर्पस मूल्य"],
        "withdrawn so far": ["अब तक निकासी", "आतापर्यंत काढलेले"],
        "Hover, tap or use the arrow keys on the chart to read any month. Source: <0>SWP statement, SBI Equity Hybrid Fund Reg Gr (PDF)</0>.": [
            "किसी भी महीने का आंकड़ा पढ़ने के लिए चार्ट पर होवर करें, टैप करें या एरो कीज का इस्तेमाल करें। स्रोत: <0>SWP स्टेटमेंट, SBI Equity Hybrid Fund Reg Gr (PDF)</0>।",
            "कोणत्याही महिन्याचा आकडा पाहण्यासाठी चार्टवर होव्हर करा, टॅप करा किंवा ॲरो कीज वापरा. स्रोत: <0>SWP स्टेटमेंट, SBI Equity Hybrid Fund Reg Gr (PDF)</0>."],
        "Hover, tap or use the arrow keys on the chart to read any month. Source: <0>SWP illustration, Bandhan Mutual Fund (PDF)</0>.": [
            "किसी भी महीने का आंकड़ा पढ़ने के लिए चार्ट पर होवर करें, टैप करें या एरो कीज का इस्तेमाल करें। स्रोत: <0>SWP उदाहरण, Bandhan Mutual Fund (PDF)</0>।",
            "कोणत्याही महिन्याचा आकडा पाहण्यासाठी चार्टवर होव्हर करा, टॅप करा किंवा ॲरो कीज वापरा. स्रोत: <0>SWP उदाहरण, Bandhan Mutual Fund (PDF)</0>."],
        "Tip: switch to <0>First {#} years</0> to see the early fall clearly. Source: <1>{#}-year SWP journey, DSP Mutual Fund (PDF)</1>.": [
            "सुझाव: शुरुआती गिरावट साफ देखने के लिए <0>पहले {#} वर्ष</0> चुनें। स्रोत: <1>{#}-वर्षीय SWP यात्रा, DSP Mutual Fund (PDF)</1>।",
            "टीप: सुरुवातीची घसरण स्पष्ट पाहण्यासाठी <0>पहिली {#} वर्षे</0> निवडा. स्रोत: <1>{#} वर्षांचा SWP प्रवास, DSP Mutual Fund (PDF)</1>."],
        "View year-by-year table": ["वर्ष-दर-वर्ष तालिका देखें", "वर्षनिहाय तक्ता पाहा"],
        "Date": ["तारीख", "तारीख"],
        "Withdrawn so far": ["अब तक निकासी", "आतापर्यंत काढलेले"],
        "vs invested": ["निवेश की तुलना में", "गुंतवणुकीच्या तुलनेत"],
        "₹{#}<0>{#} May {#}</0>": ["₹{#}<0>{#} मई {#}</0>", "₹{#}<0>{#} मे {#}</0>"],
        "₹{#}<0>{#} Dec {#}</0>": ["₹{#}<0>{#} दिसंबर {#}</0>", "₹{#}<0>{#} डिसेंबर {#}</0>"],
        "Monthly withdrawal": ["मासिक निकासी", "मासिक रक्कम"],
        "₹{#}<0>{#}% of the corpus a year</0>": ["₹{#}<0>कॉर्पस का {#}% प्रति वर्ष</0>", "₹{#}<0>कॉर्पसच्या {#}% दरवर्षी</0>"],
        "Total withdrawn": ["कुल निकासी", "एकूण काढलेली रक्कम"],
        "₹{#}<0>{#} monthly payments</0>": ["₹{#}<0>{#} मासिक भुगतान</0>", "₹{#}<0>{#} मासिक रकमा</0>"],
        "₹{#} lakh<0>{#} monthly payments</0>": ["₹{#} लाख<0>{#} मासिक भुगतान</0>", "₹{#} लाख<0>{#} मासिक रकमा</0>"],
        "Lowest corpus value": ["न्यूनतम कॉर्पस मूल्य", "किमान कॉर्पस मूल्य"],
        "₹{#} Cr<0>Jan {#} · never below ₹{#} Cr</0>": ["₹{#} करोड़<0>जनवरी {#} · कभी ₹{#} करोड़ से नीचे नहीं</0>", "₹{#} कोटी<0>जानेवारी {#} · कधीही ₹{#} कोटींच्या खाली नाही</0>"],
        "Corpus after last withdrawal": ["आखिरी निकासी के बाद कॉर्पस", "शेवटच्या रकमेनंतर कॉर्पस"],
        "Units held": ["रखी गई यूनिट्स", "धारण केलेल्या युनिट्स"],
        "{#} → {#}<0>{#}% fewer units</0>": ["{#} → {#}<0>{#}% कम यूनिट्स</0>", "{#} → {#}<0>{#}% कमी युनिट्स</0>"],
        "Return (as stated in source)": ["रिटर्न (स्रोत के अनुसार)", "परतावा (स्रोतानुसार)"],
        "What an experienced investor notices": ["एक अनुभवी निवेशक क्या देखता है", "अनुभवी गुंतवणूकदार काय लक्षात घेतो"],
        "A {#}% withdrawal rate stayed below what the fund earned, so the corpus <0>doubled while paying ₹{#} lakh</0> of income.": [
            "{#}% की निकासी दर फंड की कमाई से कम रही, इसलिए कॉर्पस <0>₹{#} लाख की आय देते हुए भी दोगुना</0> हो गया।",
            "{#}% पैसे काढण्याचा दर फंडाच्या कमाईपेक्षा कमी राहिला, त्यामुळे कॉर्पस <0>₹{#} लाखांचे उत्पन्न देत असतानाही दुप्पट</0> झाला."],
        "The cushion built in the first years absorbed the March {#} crash: the corpus only slipped to about ₹{#} Cr.": [
            "शुरुआती वर्षों में बने सुरक्षा कवच ने मार्च {#} की गिरावट झेल ली: कॉर्पस केवल लगभग ₹{#} करोड़ तक फिसला।",
            "सुरुवातीच्या वर्षांत तयार झालेल्या राखीव बळाने मार्च {#} ची घसरण पचवली: कॉर्पस फक्त सुमारे ₹{#} कोटींपर्यंत घसरला."],
        "Units fell by {#}%, yet the value rose because NAV grew {#}×. Judge an SWP by its value, not its unit count.": [
            "यूनिट्स {#}% घटीं, फिर भी मूल्य बढ़ा क्योंकि NAV {#}× बढ़ा। SWP को उसके मूल्य से आंकें, यूनिट्स की संख्या से नहीं।",
            "युनिट्स {#}% कमी झाल्या, तरीही मूल्य वाढले कारण NAV {#}× वाढला. SWP चे मूल्यमापन युनिट्सच्या संख्येवरून नव्हे, तर मूल्यावरून करा."],
        "Bandhan Aggressive Hybrid Fund (Regular Growth), Dec {#} – Jun {#}": ["Bandhan Aggressive Hybrid Fund (रेगुलर ग्रोथ), दिसंबर {#} – जून {#}", "Bandhan Aggressive Hybrid Fund (रेग्युलर ग्रोथ), डिसेंबर {#} – जून {#}"],
        "₹{#} L<0>{#} Apr {#} · {#}% below invested</0>": ["₹{#} लाख<0>{#} अप्रैल {#} · निवेश से {#}% कम</0>", "₹{#} लाख<0>{#} एप्रिल {#} · गुंतवणुकीपेक्षा {#}% कमी</0>"],
        "₹{#} L<0>{#} Oct {#} · {#}% below invested</0>": ["₹{#} लाख<0>{#} अक्टूबर {#} · निवेश से {#}% कम</0>", "₹{#} लाख<0>{#} ऑक्टोबर {#} · गुंतवणुकीपेक्षा {#}% कमी</0>"],
        "Corpus on {#} Jun {#}": ["{#} जून {#} को कॉर्पस", "{#} जून {#} रोजी कॉर्पस"],
        "Corpus on {#} Aug {#}": ["{#} अगस्त {#} को कॉर्पस", "{#} ऑगस्ट {#} रोजी कॉर्पस"],
        "Tax paid (per the illustration)": ["चुकाया गया टैक्स (उदाहरण के अनुसार)", "भरलेला कर (उदाहरणानुसार)"],
        "₹{#}<0>on ₹{#} lakh withdrawn</0>": ["₹{#}<0>₹{#} लाख की निकासी पर</0>", "₹{#}<0>₹{#} लाख काढलेल्या रकमेवर</0>"],
        "XIRR (as stated in source)": ["XIRR (स्रोत के अनुसार)", "XIRR (स्रोतानुसार)"],
        "{#}% is a demanding withdrawal rate. The corpus stayed <0>below ₹{#} Cr for {#} months in a row</0> (Oct {#} – May {#}) and lost a third of its value in the COVID crash.": [
            "{#1}% एक ऊंची निकासी दर है। कॉर्पस <0>लगातार {#3} महीनों तक ₹{#2} करोड़ से नीचे</0> रहा (अक्टूबर {#4} – मई {#5}) और कोविड गिरावट में उसका एक-तिहाई मूल्य घट गया।",
            "{#1}% हा जास्त पैसे काढण्याचा दर आहे. कॉर्पस <0>सलग {#3} महिने ₹{#2} कोटींच्या खाली</0> राहिला (ऑक्टोबर {#4} – मे {#5}) आणि कोविड घसरणीत त्याचे एक-तृतीयांश मूल्य घटले."],
        "It recovered only because the investor kept the plan running while NAV rebounded from {#} to {#}. Stopping and exiting in April {#} would have locked in the fall.": [
            "यह केवल इसलिए उबर पाया क्योंकि NAV के {#} से {#} तक लौटने के दौरान निवेशक ने योजना जारी रखी। अप्रैल {#} में रोककर बाहर निकलने से गिरावट स्थायी हो जाती।",
            "NAV {#} वरून {#} पर्यंत परत वाढत असताना गुंतवणूकदाराने योजना सुरू ठेवल्यामुळेच तो सावरला. एप्रिल {#} मध्ये थांबून बाहेर पडल्यास घसरण कायमची झाली असती."],
        "Tax was about {#}% of the money withdrawn, because only the gain part of each redemption is taxed (on the fund house's stated assumptions).": [
            "टैक्स निकाली गई राशि का लगभग {#}% था, क्योंकि हर रिडेम्पशन में केवल लाभ वाले हिस्से पर टैक्स लगता है (फंड हाउस की बताई गई मान्यताओं के अनुसार)।",
            "कर काढलेल्या रकमेच्या सुमारे {#}% होता, कारण प्रत्येक रिडेम्पशनमधील फक्त नफ्याच्या भागावर कर लागतो (फंड हाऊसने नमूद केलेल्या गृहीतकांनुसार)."],
        "DSP Aggressive Hybrid Fund, May {#} – Aug {#}": ["DSP Aggressive Hybrid Fund, मई {#} – अगस्त {#}", "DSP Aggressive Hybrid Fund, मे {#} – ऑगस्ट {#}"],
        "The plan started just before the {#}–{#} bear market. Within {#}½ years ₹{#} lakh had become ₹{#} lakh, and it stayed below ₹{#} lakh until Aug {#}. This is <0>sequence risk</0>: bad years at the start of an SWP hurt the most.": [
            "योजना {#1}–{#2} की मंदी से ठीक पहले शुरू हुई। {#3}½ वर्षों के भीतर ₹{#4} लाख घटकर ₹{#5} लाख रह गए, और अगस्त {#7} तक यह ₹{#6} लाख से नीचे रहा। यही <0>सीक्वेंस रिस्क</0> है: SWP की शुरुआत के खराब वर्ष सबसे ज्यादा नुकसान करते हैं।",
            "योजना {#1}–{#2} च्या मंदीच्या अगदी आधी सुरू झाली. {#3}½ वर्षांत ₹{#4} लाखांचे ₹{#5} लाख झाले, आणि ऑगस्ट {#7} पर्यंत ते ₹{#6} लाखांच्या खाली राहिले. यालाच <0>सीक्वेन्स रिस्क</0> म्हणतात: SWP च्या सुरुवातीची वाईट वर्षे सर्वात जास्त नुकसान करतात."],
        "Staying invested for {#} years turned ₹{#} lakh into ₹{#} lakh of income <0>plus</0> a ₹{#} crore corpus.": [
            "{#} वर्षों तक निवेशित रहने से ₹{#} लाख, ₹{#} लाख की आय <0>और साथ में</0> ₹{#} करोड़ के कॉर्पस में बदल गए।",
            "{#} वर्षे गुंतवणूक टिकवल्यामुळे ₹{#} लाखांचे ₹{#} लाखांचे उत्पन्न <0>आणि त्यासोबत</0> ₹{#} कोटींचा कॉर्पस झाला."],
        "Units fell by {#}% while NAV rose {#}×. Time in the market did the heavy lifting.": [
            "यूनिट्स {#}% घटीं, जबकि NAV {#}× बढ़ा। असली काम बाजार में बिताए गए समय ने किया।",
            "युनिट्स {#}% कमी झाल्या, तर NAV {#}× वाढला. खरे काम बाजारात घालवलेल्या वेळेने केले."],
        "Side by side: the three journeys": ["आमने-सामने: तीनों यात्राएं", "शेजारी शेजारी: तिन्ही प्रवास"],
        "Same idea, different withdrawal rates, start dates and market conditions. Every figure below is taken from, or counted from, the source statements.": [
            "एक ही विचार, अलग-अलग निकासी दरें, शुरुआती तारीखें और बाजार की स्थितियां। नीचे का हर आंकड़ा स्रोत स्टेटमेंट से लिया गया है या उन्हीं से गिना गया है।",
            "एकच संकल्पना, वेगवेगळे पैसे काढण्याचे दर, सुरुवातीच्या तारखा आणि बाजार परिस्थिती. खालील प्रत्येक आकडा मूळ स्टेटमेंट्समधून घेतलेला किंवा त्यावरून मोजलेला आहे."],
        "Swipe the table sideways to compare all three funds": ["तीनों फंड की तुलना के लिए तालिका को बगल में स्वाइप करें", "तिन्ही फंडांची तुलना करण्यासाठी तक्ता बाजूला स्वाइप करा"],
        "May {#} – May {#}<0>{#} years</0>": ["मई {#} – मई {#}<0>{#} वर्ष</0>", "मे {#} – मे {#}<0>{#} वर्षे</0>"],
        "Dec {#} – Jun {#}<0>{#} years</0>": ["दिसंबर {#} – जून {#}<0>{#} वर्ष</0>", "डिसेंबर {#} – जून {#}<0>{#} वर्षे</0>"],
        "May {#} – Aug {#}<0>{#} years</0>": ["मई {#} – अगस्त {#}<0>{#} वर्ष</0>", "मे {#} – ऑगस्ट {#}<0>{#} वर्षे</0>"],
        "Amount invested": ["निवेशित राशि", "गुंतवलेली रक्कम"],
        "Withdrawal rate (a year)": ["निकासी दर (प्रति वर्ष)", "पैसे काढण्याचा दर (दरवर्षी)"],
        "₹{#} Cr<0>never below invested</0>": ["₹{#} करोड़<0>कभी निवेश से नीचे नहीं</0>", "₹{#} कोटी<0>कधीही गुंतवणुकीच्या खाली नाही</0>"],
        "₹{#} L<0>−{#}% (Apr {#})</0>": ["₹{#} लाख<0>−{#}% (अप्रैल {#})</0>", "₹{#} लाख<0>−{#}% (एप्रिल {#})</0>"],
        "₹{#} L<0>−{#}% (Oct {#})</0>": ["₹{#} लाख<0>−{#}% (अक्टूबर {#})</0>", "₹{#} लाख<0>−{#}% (ऑक्टोबर {#})</0>"],
        "Longest stretch below amount invested": ["निवेशित राशि से नीचे रहने की सबसे लंबी अवधि", "गुंतवलेल्या रकमेखाली राहण्याचा सर्वात मोठा कालावधी"],
        "None": ["कोई नहीं", "नाही"],
        "{#} months": ["{#} महीने", "{#} महिने"],
        "Corpus at the end": ["अंत में कॉर्पस", "शेवटी कॉर्पस"],
        "₹{#} Cr<0>{#}× invested</0>": ["₹{#} करोड़<0>निवेश का {#}×</0>", "₹{#} कोटी<0>गुंतवणुकीच्या {#}×</0>"],
        "Units remaining": ["शेष यूनिट्स", "उर्वरित युनिट्स"],
        "{#}% of original": ["मूल का {#}%", "मूळच्या {#}%"],
        "Return (as stated)": ["रिटर्न (बताए अनुसार)", "परतावा (नमूद केल्याप्रमाणे)"],
        "Expert Takeaways": ["विशेषज्ञ निष्कर्ष", "तज्ज्ञांचे निष्कर्ष"],
        "Six lessons to take into your own SWP": ["अपने SWP के लिए छह सबक", "तुमच्या स्वतःच्या SWP साठी सहा धडे"],
        "The withdrawal rate decides the ride": ["निकासी दर ही सफर तय करती है", "पैसे काढण्याचा दरच प्रवास ठरवतो"],
        "Both {#}% plans grew their corpus strongly. The {#}% plan still ended up, but spent {#} months below the starting amount. Starting around {#}–{#}% of the corpus a year leaves room for bad years.": [
            "{#}% वाली दोनों योजनाओं ने अपना कॉर्पस काफी बढ़ाया। {#}% वाली योजना भी आखिर में बढ़त पर रही, लेकिन {#} महीने शुरुआती राशि से नीचे रही। सालाना कॉर्पस के लगभग {#}–{#}% से शुरुआत करने पर खराब वर्षों के लिए गुंजाइश बनी रहती है।",
            "{#}% च्या दोन्ही योजनांनी कॉर्पस जोरदार वाढवला. {#}% ची योजनाही शेवटी वाढीत राहिली, पण {#} महिने सुरुवातीच्या रकमेखाली होती. दरवर्षी कॉर्पसच्या सुमारे {#}–{#}% पासून सुरुवात केल्यास वाईट वर्षांसाठी वाव राहतो."],
        "The first few years matter most": ["शुरुआती कुछ वर्ष सबसे ज्यादा मायने रखते हैं", "सुरुवातीची काही वर्षे सर्वात महत्त्वाची"],
        "The DSP plan lost {#}% in its first {#}½ years. A crash early in an SWP hurts more than a crash later. Keep a cash buffer in a liquid fund (see the Cash Buffer Strategy above) so a fall does not force panic decisions.": [
            "DSP योजना ने अपने पहले {#2}½ वर्षों में {#1}% खोया। SWP की शुरुआत में आई गिरावट बाद की गिरावट से ज्यादा नुकसान करती है। लिक्विड फंड में नकद बफर रखें (ऊपर नकद बफर रणनीति देखें), ताकि गिरावट आपको घबराहट में फैसले लेने पर मजबूर न करे।",
            "DSP योजनेने पहिल्या {#2}½ वर्षांत {#1}% गमावले. SWP च्या सुरुवातीची घसरण नंतरच्या घसरणीपेक्षा जास्त नुकसान करते. लिक्विड फंडात रोख राखीव ठेवा (वरील रोख राखीव रणनीती पाहा), म्हणजे घसरणीमुळे घाबरून निर्णय घ्यावे लागणार नाहीत."],
        "Watch the value, not the units": ["यूनिट्स नहीं, मूल्य पर नजर रखें", "युनिट्सवर नव्हे, मूल्यावर लक्ष ठेवा"],
        "Every SWP sells units; the unit count fell {#}–{#}% in all three cases. What matters is whether NAV growth outpaces the units sold. In all three, it did over the full period.": [
            "हर SWP यूनिट्स बेचता है; तीनों मामलों में यूनिट्स की संख्या {#}–{#}% घटी। मायने यह रखता है कि NAV की बढ़त बेची गई यूनिट्स से आगे रहती है या नहीं। तीनों में पूरी अवधि में ऐसा ही हुआ।",
            "प्रत्येक SWP युनिट्स विकते; तिन्ही उदाहरणांत युनिट्सची संख्या {#}–{#}% कमी झाली. महत्त्वाचे हे आहे की NAV ची वाढ विकलेल्या युनिट्सपेक्षा पुढे राहते का. तिन्हींमध्ये संपूर्ण कालावधीत तसेच झाले."],
        "These are equity-heavy funds": ["ये इक्विटी-प्रधान फंड हैं", "हे इक्विटी-प्रधान फंड आहेत"],
        "Aggressive hybrid funds keep most of their money ({#}–{#}%) in equity. The DSP and Bandhan documents rate their schemes <0>Very High</0> risk. Use them for an SWP only with a {#}+ year horizon and the patience to sit through {#}% falls.": [
            "एग्रेसिव हाइब्रिड फंड अपना अधिकांश पैसा ({#}–{#}%) इक्विटी में रखते हैं। DSP और Bandhan के दस्तावेज अपनी योजनाओं को <0>बहुत अधिक</0> जोखिम वाली बताते हैं। SWP के लिए इनका उपयोग केवल {#}+ वर्ष की अवधि और {#}% गिरावट झेलने के धैर्य के साथ करें।",
            "ॲग्रेसिव्ह हायब्रिड फंड आपला बहुतांश पैसा ({#}–{#}%) इक्विटीत ठेवतात. DSP आणि Bandhan ची कागदपत्रे त्यांच्या योजनांना <0>अतिशय जास्त</0> जोखमीच्या म्हणून दर्शवतात. SWP साठी त्यांचा वापर फक्त {#}+ वर्षांचा कालावधी आणि {#}% घसरण सहन करण्याच्या संयमासहच करा."],
        "Tax applies only to the gain": ["टैक्स केवल लाभ पर लगता है", "कर फक्त नफ्यावर लागतो"],
        "Each withdrawal is part your own capital and part gain, and only the gain is taxed. In the Bandhan illustration, tax was ₹{#} lakh on ₹{#} lakh withdrawn, about {#}%. Your actual tax depends on your situation; please consult your tax advisor.": [
            "हर निकासी का एक हिस्सा आपकी अपनी पूंजी और एक हिस्सा लाभ होता है, और टैक्स केवल लाभ पर लगता है। Bandhan के उदाहरण में ₹{#2} लाख की निकासी पर टैक्स ₹{#1} लाख था, यानी लगभग {#3}%। आपका वास्तविक टैक्स आपकी स्थिति पर निर्भर करता है; कृपया अपने कर सलाहकार से परामर्श लें।",
            "प्रत्येक रकमेचा एक भाग तुमचे स्वतःचे भांडवल आणि एक भाग नफा असतो, आणि कर फक्त नफ्यावर लागतो. Bandhan च्या उदाहरणात ₹{#2} लाख काढलेल्या रकमेवर कर ₹{#1} लाख होता, म्हणजे सुमारे {#3}%. तुमचा प्रत्यक्ष कर तुमच्या परिस्थितीवर अवलंबून असतो; कृपया तुमच्या कर सल्लागाराचा सल्ला घ्या."],
        "Check the exit load in year one": ["पहले वर्ष में एग्जिट लोड जांचें", "पहिल्या वर्षी एक्झिट लोड तपासा"],
        "In the SBI and Bandhan statements, up to {#}% of units can be redeemed free in the first {#} months, with {#}% charged on the rest. A {#}–{#}% yearly withdrawal stays inside that limit. Always check your scheme's exit load before you start.": [
            "SBI और Bandhan के स्टेटमेंट में पहले {#2} महीनों में {#1}% तक यूनिट्स मुफ्त रिडीम की जा सकती हैं, बाकी पर {#3}% शुल्क लगता है। {#4}–{#5}% की सालाना निकासी इस सीमा के भीतर रहती है। शुरू करने से पहले हमेशा अपनी योजना का एग्जिट लोड जांचें।",
            "SBI आणि Bandhan च्या स्टेटमेंट्समध्ये पहिल्या {#2} महिन्यांत {#1}% पर्यंत युनिट्स विनाशुल्क रिडीम करता येतात, उरलेल्यांवर {#3}% शुल्क लागते. {#4}–{#5}% वार्षिक रक्कम या मर्यादेत राहते. सुरुवात करण्यापूर्वी नेहमी तुमच्या योजनेचा एक्झिट लोड तपासा."],
        "From the same DSP document": ["उसी DSP दस्तावेज से", "त्याच DSP कागदपत्रातून"],
        "The same ₹{#} a month, in both directions": ["हर महीने वही ₹{#}, दोनों दिशाओं में", "दरमहा तेच ₹{#}, दोन्ही दिशांनी"],
        "Build the corpus with an SIP while you earn, then draw an income from it with an SWP when you need it. The DSP document shows both over the same {#} years, in two different funds:": [
            "कमाई के दौरान SIP से कॉर्पस बनाएं, फिर जरूरत पड़ने पर SWP से उससे आय लें। DSP दस्तावेज उन्हीं {#} वर्षों में दो अलग-अलग फंड में दोनों को दिखाता है:",
            "कमावत असताना SIP ने कॉर्पस उभारा, मग गरज असेल तेव्हा SWP ने त्यातून उत्पन्न घ्या. DSP चे कागदपत्र त्याच {#} वर्षांत दोन वेगवेगळ्या फंडांमध्ये दोन्ही दाखवते:"],
        "₹{#} invested every month from Jun {#}: ₹{#} lakh put in, valued at ₹{#} crore on {#} Aug {#} (XIRR {#}%).": [
            "जून {#2} से हर महीने ₹{#1} का निवेश: कुल ₹{#3} लाख लगाए गए, जिनका मूल्य {#5} अगस्त {#6} को ₹{#4} करोड़ था (XIRR {#7}%)।",
            "जून {#2} पासून दरमहा ₹{#1} ची गुंतवणूक: एकूण ₹{#3} लाख गुंतवले, ज्यांचे मूल्य {#5} ऑगस्ट {#6} रोजी ₹{#4} कोटी होते (XIRR {#7}%)."],
        "₹{#} taken out every month from Jun {#}: ₹{#} lakh received as income, with ₹{#} crore still invested on {#} Aug {#}.": [
            "जून {#2} से हर महीने ₹{#1} की निकासी: ₹{#3} लाख आय के रूप में मिले, और {#5} अगस्त {#6} को भी ₹{#4} करोड़ निवेशित रहे।",
            "जून {#2} पासून दरमहा ₹{#1} काढले: ₹{#3} लाख उत्पन्न म्हणून मिळाले, आणि {#5} ऑगस्ट {#6} रोजीही ₹{#4} कोटी गुंतवलेले राहिले."],
        "The two funds carry different levels of risk; this is not a like-for-like comparison. Flexi Cap SIP values use the Dividend Reinvestment NAV until June {#} and Regular Plan – Growth NAV thereafter, as noted in the source. <0>Learn how an SIP works →</0>": [
            "दोनों फंड में जोखिम का स्तर अलग है; यह समान स्तर की तुलना नहीं है। स्रोत में बताए अनुसार, फ्लेक्सी कैप SIP के मूल्य जून {#} तक डिविडेंड रीइन्वेस्टमेंट NAV और उसके बाद रेगुलर प्लान – ग्रोथ NAV पर आधारित हैं। <0>जानें SIP कैसे काम करता है →</0>",
            "दोन्ही फंडांतील जोखमीचा स्तर वेगळा आहे; ही समान पातळीवरील तुलना नाही. स्रोतात नमूद केल्याप्रमाणे, फ्लेक्सी कॅप SIP ची मूल्ये जून {#} पर्यंत डिव्हिडंड रीइन्व्हेस्टमेंट NAV आणि त्यानंतर रेग्युलर प्लॅन – ग्रोथ NAV वर आधारित आहेत. <0>SIP कशी काम करते ते जाणून घ्या →</0>"],
        "Sources & important disclosures": ["स्रोत और महत्वपूर्ण प्रकटीकरण", "स्रोत आणि महत्त्वाची प्रकटीकरणे"],
        "<0>Mutual Fund SWP Calculator – SBI Equity Hybrid Fund Reg Gr</0> (lumpsum {#}-{#}-{#}, SWP {#}-{#}-{#} to {#}-{#}-{#}).": [
            "<0>Mutual Fund SWP Calculator – SBI Equity Hybrid Fund Reg Gr</0> (एकमुश्त {#}-{#}-{#}, SWP {#}-{#}-{#} से {#}-{#}-{#})।",
            "<0>Mutual Fund SWP Calculator – SBI Equity Hybrid Fund Reg Gr</0> (एकरकमी {#}-{#}-{#}, SWP {#}-{#}-{#} ते {#}-{#}-{#})."],
        "<0>SWP Illustration: Bandhan Aggressive Hybrid Fund, Bandhan Mutual Fund</0> (NAV as on {#} June {#}, Regular Growth; tax at {#}% STCG / {#}% LTCG, STT excluded, no grandfathering, as stated by the fund house).": [
            "<0>SWP Illustration: Bandhan Aggressive Hybrid Fund, Bandhan Mutual Fund</0> ({#} जून {#} तक का NAV, रेगुलर ग्रोथ; {#}% STCG / {#}% LTCG की दर से टैक्स, STT शामिल नहीं, ग्रैंडफादरिंग नहीं, जैसा फंड हाउस ने बताया है)।",
            "<0>SWP Illustration: Bandhan Aggressive Hybrid Fund, Bandhan Mutual Fund</0> ({#} जून {#} रोजीचा NAV, रेग्युलर ग्रोथ; {#}% STCG / {#}% LTCG दराने कर, STT वगळून, ग्रँडफादरिंग नाही, फंड हाऊसने नमूद केल्याप्रमाणे)."],
        "<0>{#} Years Regular Cash Flow + Wealth Creation Journey of DSP Aggressive Hybrid Fund, DSP Mutual Fund</0> (data as on {#} August {#}), including the DSP Flexi Cap SIP workflow, SEBI-format returns and riskometers.": [
            "<0>{#} Years Regular Cash Flow + Wealth Creation Journey of DSP Aggressive Hybrid Fund, DSP Mutual Fund</0> ({#} अगस्त {#} तक का डेटा), जिसमें DSP फ्लेक्सी कैप SIP की प्रक्रिया, SEBI-प्रारूप रिटर्न और रिस्कोमीटर शामिल हैं।",
            "<0>{#} Years Regular Cash Flow + Wealth Creation Journey of DSP Aggressive Hybrid Fund, DSP Mutual Fund</0> ({#} ऑगस्ट {#} रोजीची आकडेवारी), ज्यात DSP फ्लेक्सी कॅप SIP ची प्रक्रिया, SEBI-स्वरूपातील परतावा आणि रिस्कोमीटर समाविष्ट आहेत."],
        "These case studies are shared for investor education only. They are illustrations based on historical NAVs published by the respective fund houses, and are not a recommendation to buy, hold or sell any scheme. Past performance may or may not be sustained in future. Actual results will vary with the scheme, plan, start date, withdrawal amount, taxes and exit loads. Please read the complete source documents and the scheme-related documents before investing.": [
            "ये केस स्टडी केवल निवेशक शिक्षा के लिए साझा की गई हैं। ये संबंधित फंड हाउस द्वारा प्रकाशित ऐतिहासिक NAV पर आधारित उदाहरण हैं, और किसी भी योजना को खरीदने, रखने या बेचने की सिफारिश नहीं हैं। पिछला प्रदर्शन भविष्य में बना रह भी सकता है और नहीं भी। वास्तविक परिणाम योजना, प्लान, शुरुआती तारीख, निकासी राशि, टैक्स और एग्जिट लोड के अनुसार अलग होंगे। निवेश से पहले कृपया पूरे स्रोत दस्तावेज और योजना से संबंधित दस्तावेज पढ़ें।",
            "या केस स्टडी केवळ गुंतवणूकदार शिक्षणासाठी दिल्या आहेत. ही संबंधित फंड हाऊसेसनी प्रकाशित केलेल्या ऐतिहासिक NAV वर आधारित उदाहरणे आहेत आणि कोणतीही योजना खरेदी करण्याची, ठेवण्याची किंवा विकण्याची शिफारस नाही. मागील कामगिरी भविष्यात टिकेलच असे नाही. प्रत्यक्ष परिणाम योजना, प्लॅन, सुरुवातीची तारीख, काढलेली रक्कम, कर आणि एक्झिट लोडनुसार बदलतील. गुंतवणूक करण्यापूर्वी कृपया संपूर्ण मूळ कागदपत्रे आणि योजनेशी संबंधित कागदपत्रे वाचा."],
        "Mutual Fund investments are subject to market risks, read all scheme related documents carefully.": [
            "म्यूचुअल फंड निवेश बाजार जोखिमों के अधीन हैं, योजना से संबंधित सभी दस्तावेज ध्यान से पढ़ें।",
            "म्युच्युअल फंड गुंतवणूक बाजारातील जोखमींच्या अधीन असते, योजनेशी संबंधित सर्व कागदपत्रे काळजीपूर्वक वाचा."],
        "Frequently Asked Questions About SWP": ["SWP के बारे में अक्सर पूछे जाने वाले प्रश्न", "SWP बद्दल वारंवार विचारले जाणारे प्रश्न"],
        "How is SWP taxed compared to Fixed Deposit interest?": ["फिक्स्ड डिपॉजिट ब्याज की तुलना में SWP पर टैक्स कैसे लगता है?", "मुदत ठेवीच्या व्याजाच्या तुलनेत SWP वर कर कसा लागतो?"],
        "In a Fixed Deposit, {#}% of the interest earned is added to your annual income and taxed at your peak slab rate (up to {#}% + cess). In an SWP, each withdrawal is a redemption of units comprising both your original principal and capital gain. Tax applies <0>only to the capital gain portion</0>. For equity-oriented funds held over {#} months, Long-Term Capital Gains up to ₹{#} Lakh per financial year are tax-exempt, and gains beyond ₹{#} Lakh are taxed at only {#}%, making SWP far more tax-efficient.": [
            "फिक्स्ड डिपॉजिट में कमाए गए ब्याज का {#}% आपकी वार्षिक आय में जुड़ता है और आपके उच्चतम स्लैब रेट (अधिकतम {#}% + सेस) पर टैक्स लगता है। SWP में हर निकासी यूनिट्स का रिडेम्पशन होती है, जिसमें आपका मूल धन और पूंजीगत लाभ दोनों शामिल होते हैं। टैक्स <0>केवल पूंजीगत लाभ वाले हिस्से पर</0> लगता है। {#} महीनों से अधिक रखे गए इक्विटी-उन्मुख फंड पर प्रति वित्तीय वर्ष ₹{#} लाख तक का दीर्घकालिक पूंजीगत लाभ कर-मुक्त है, और ₹{#} लाख से अधिक के लाभ पर केवल {#}% टैक्स लगता है, जिससे SWP कहीं अधिक कर-कुशल बनता है।",
            "मुदत ठेवीत मिळालेल्या व्याजापैकी {#}% रक्कम तुमच्या वार्षिक उत्पन्नात जोडली जाते आणि तुमच्या सर्वोच्च स्लॅब दराने (कमाल {#}% + उपकर) कर लागतो. SWP मध्ये प्रत्येक रक्कम म्हणजे युनिट्सचे रिडेम्पशन असते, ज्यात तुमचे मूळ मुद्दल आणि भांडवली नफा दोन्ही असतात. कर <0>फक्त भांडवली नफ्याच्या भागावर</0> लागतो. {#} महिन्यांपेक्षा जास्त काळ ठेवलेल्या इक्विटी-आधारित फंडांवर प्रत्येक आर्थिक वर्षात ₹{#} लाखांपर्यंतचा दीर्घकालीन भांडवली नफा करमुक्त असतो आणि ₹{#} लाखांपेक्षा जास्त नफ्यावर फक्त {#}% कर लागतो, त्यामुळे SWP खूपच अधिक कर-कार्यक्षम ठरते."],
        "Can I change my monthly withdrawal amount or date later?": ["क्या मैं बाद में अपनी मासिक निकासी राशि या तारीख बदल सकता हूं?", "मी नंतर मासिक रक्कम किंवा तारीख बदलू शकतो का?"],
        "Yes. You have complete flexibility. You can modify your withdrawal amount, alter your preferred payout date, change the frequency (e.g., from monthly to quarterly), or stop the mandate entirely at any time without any financial penalty or lock-in fee.": [
            "हां। आपको पूरा लचीलापन मिलता है। आप कभी भी बिना किसी वित्तीय जुर्माने या लॉक-इन शुल्क के अपनी निकासी राशि बदल सकते हैं, भुगतान की पसंदीदा तारीख बदल सकते हैं, आवृत्ति बदल सकते हैं (जैसे मासिक से तिमाही), या मैंडेट पूरी तरह बंद कर सकते हैं।",
            "होय. तुम्हाला पूर्ण लवचिकता मिळते. तुम्ही कधीही कोणताही आर्थिक दंड किंवा लॉक-इन शुल्क न भरता रक्कम बदलू शकता, पसंतीची तारीख बदलू शकता, वारंवारता बदलू शकता (उदा. मासिकवरून तिमाही) किंवा मँडेट पूर्णपणे बंद करू शकता."],
        "What happens to my SWP during a short-term market downturn?": ["अल्पकालिक बाजार गिरावट के दौरान मेरे SWP का क्या होता है?", "अल्पकालीन बाजार घसरणीत माझ्या SWP चे काय होते?"],
        "Because your withdrawal amount in rupees remains fixed, a lower NAV means a slightly higher number of units are redeemed to generate your payout. To cushion against this, our advisors recommend setting up SWPs from Balanced Advantage or Hybrid funds, which have debt cushions, and keeping an emergency liquid reserve of {#}–{#} months' expenses.": [
            "चूंकि रुपये में आपकी निकासी राशि तय रहती है, इसलिए NAV कम होने पर भुगतान के लिए थोड़ी ज्यादा यूनिट्स रिडीम होती हैं। इससे बचाव के लिए हमारे सलाहकार डेट सुरक्षा वाले बैलेंस्ड एडवांटेज या हाइब्रिड फंड से SWP शुरू करने और {#}–{#} महीनों के खर्च का आपातकालीन लिक्विड रिजर्व रखने की सलाह देते हैं।",
            "रुपयांमधील तुमची रक्कम निश्चित असल्याने NAV कमी झाल्यास रक्कम देण्यासाठी थोड्या जास्त युनिट्स रिडीम होतात. यापासून बचावासाठी आमचे सल्लागार डेटचे संरक्षण असलेल्या बॅलन्स्ड ॲडव्हान्टेज किंवा हायब्रिड फंडांमधून SWP सुरू करण्याचा आणि {#}–{#} महिन्यांच्या खर्चाइतका आपत्कालीन लिक्विड राखीव निधी ठेवण्याचा सल्ला देतात."],
        "Which fund categories are best suited for an SWP?": ["SWP के लिए कौन-सी फंड श्रेणियां सबसे उपयुक्त हैं?", "SWP साठी कोणत्या फंड श्रेणी सर्वात योग्य आहेत?"],
        "For regular living expenses or retirement payouts, hybrid categories such as Balanced Advantage Funds (BAFs), Multi-Asset Allocation Funds, and Conservative/Equity Savings Funds are most popular because they manage equity risk dynamically while preserving capital.": [
            "नियमित जीवन-यापन खर्च या सेवानिवृत्ति भुगतान के लिए बैलेंस्ड एडवांटेज फंड (BAF), मल्टी-एसेट एलोकेशन फंड और कंजर्वेटिव/इक्विटी सेविंग्स फंड जैसी हाइब्रिड श्रेणियां सबसे लोकप्रिय हैं, क्योंकि ये पूंजी को सुरक्षित रखते हुए इक्विटी जोखिम को गतिशील रूप से संभालती हैं।",
            "नियमित राहणीमान खर्च किंवा सेवानिवृत्तीतील रकमांसाठी बॅलन्स्ड ॲडव्हान्टेज फंड (BAF), मल्टि-ॲसेट ॲलोकेशन फंड आणि कन्झर्व्हेटिव्ह/इक्विटी सेव्हिंग्ज फंड यांसारख्या हायब्रिड श्रेणी सर्वात लोकप्रिय आहेत, कारण त्या भांडवल जपत इक्विटी जोखीम गतिमानपणे सांभाळतात."],
        "Part {#}: Wealth Accumulation": ["भाग {#}: संपत्ति संचय", "भाग {#}: संपत्ती संचय"],
        "Looking to Build Your Investment Corpus First? Explore": ["पहले अपना निवेश कॉर्पस बनाना चाहते हैं? देखें", "आधी तुमचा गुंतवणूक कॉर्पस उभारायचा आहे? पाहा"],
        "Start small and build a substantial corpus over time with disciplined monthly investing, rupee-cost averaging, and the power of compounding on our dedicated SIP page.": [
            "हमारे समर्पित SIP पेज पर जानें कि छोटी शुरुआत करके अनुशासित मासिक निवेश, रुपी-कॉस्ट एवरेजिंग और चक्रवृद्धि की ताकत से समय के साथ बड़ा कॉर्पस कैसे बनाया जा सकता है।",
            "आमच्या खास SIP पानावर जाणून घ्या की लहान सुरुवात करून शिस्तबद्ध मासिक गुंतवणूक, रुपी-कॉस्ट ॲव्हरेजिंग आणि चक्रवाढीच्या ताकदीने कालांतराने मोठा कॉर्पस कसा उभारता येतो."],
        "Explore SIP Solutions": ["SIP समाधान देखें", "SIP उपाय पाहा"],
        "SBI Equity Hybrid Fund: SWP statement, ₹{#} Cr, May {#} – May {#} (PDF, {#} pages)": ["SBI Equity Hybrid Fund: SWP स्टेटमेंट, ₹{#} करोड़, मई {#} – मई {#} (PDF, {#} पृष्ठ)", "SBI Equity Hybrid Fund: SWP स्टेटमेंट, ₹{#} कोटी, मे {#} – मे {#} (PDF, {#} पाने)"],
        "DSP Aggressive Hybrid Fund: {#}-year SWP journey, ₹{#} L, {#} – {#} (PDF, {#} pages)": ["DSP Aggressive Hybrid Fund: {#}-वर्षीय SWP यात्रा, ₹{#} लाख, {#} – {#} (PDF, {#} पृष्ठ)", "DSP Aggressive Hybrid Fund: {#} वर्षांचा SWP प्रवास, ₹{#} लाख, {#} – {#} (PDF, {#} पाने)"],
        "Bandhan Aggressive Hybrid Fund: SWP illustration, ₹{#} Cr, Dec {#} – Jun {#} (PDF, {#} pages)": ["Bandhan Aggressive Hybrid Fund: SWP उदाहरण, ₹{#} करोड़, दिसंबर {#} – जून {#} (PDF, {#} पृष्ठ)", "Bandhan Aggressive Hybrid Fund: SWP उदाहरण, ₹{#} कोटी, डिसेंबर {#} – जून {#} (PDF, {#} पाने)"]
    });
})(window);
