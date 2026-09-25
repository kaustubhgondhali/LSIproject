# Lord Sai — Share Market Academy & Lord Sai Investment website

A **frontend-only** static website (HTML, CSS, JavaScript, Bootstrap 5, jQuery). There is no
server, database or API: every page works when served as plain files (GitHub Pages, any static
host, or VS Code Live Server).

The site has two modes that share the same pages:

- **Share Market Academy** (`?mode=academy`): courses, store, blog, stories, testimonials, contact.
- **Lord Sai Investment / Mutual Fund** (`?mode=mutual-fund`): SIP, SWP, insurance, investments, financial planning.

`index.html` is the gateway where visitors pick one of the two.

## Run it locally

Open the folder in VS Code and start **Live Server** on `index.html`, or run any static file
server in the project folder, for example:

```
python -m http.server 5500
```

Then open `http://127.0.0.1:5500/index.html`.

## Project layout

| Path | What it is |
|---|---|
| `*.html` | The pages |
| `css/style.css` | All site styles (Bootstrap is `css/bootstrap.min.css`) |
| `js/main.js` | Shared page behaviour: calculators, sliders, the enquiry pop-up form |
| `js/mode.js` | Share Market / Mutual Fund mode switching |
| `js/lang.js`, `js/translations.js`, `js/i18n/` | English / Hindi / Marathi language switcher |
| `js/enquiry.js` | Sends form enquiries and reviews to the owner on WhatsApp and by email |
| `js/ebook-access.js` | Store page: e-book opened with an access code |
| `js/blog.js`, `js/reviews.js` | Blog search / category filter; testimonials review form |
| `img/`, `pdf/`, `docs/`, `lib/` | Images, PDFs, SWP case-study PDFs, third-party libraries |

## Enquiries on WhatsApp and email

The website has no server. These forms reach the owner in two ways at once:

- the Contact page form (`contact.html`)
- the "Enquire" pop-up on most pages (`#demoModal`, handled in `js/main.js`)
- the "Add Your Review" form on `testimonials.html` (`js/reviews.js`)

1. **WhatsApp (+91 99202 54354):** submitting opens WhatsApp (the app on phones, WhatsApp Web
   or Desktop on computers) with the name, phone, email, interest and message already typed in a
   chat to the owner, and the visitor taps **Send**. WhatsApp does not let a website send a
   message by itself. The form also shows a "Tap here" link in case WhatsApp did not open.
2. **Email (lordsai.academy@gmail.com):** the same details are emailed in the background, even
   if the visitor never sends the WhatsApp message. When the email goes through, the form adds
   "A copy has also been emailed to us".

**Email setup: one click, no account or key ([FormSubmit](https://formsubmit.co)):**

1. Put the site online and submit any enquiry form once, for example a test enquiry.
2. FormSubmit sends an **"Activate Form"** email to `lordsai.academy@gmail.com`. Open it and
   click the button. Check Spam/Promotions if it is not in the inbox.
3. From then on, every enquiry arrives as an email. The enquiry that triggered the activation
   may not be delivered itself, so send one more test enquiry after activating.

Optional:

- **Hide the address:** after activation, FormSubmit's email includes a random code. Paste that
  code in place of the address in `email:` in `js/enquiry.js`, so the address is not visible
  in the page source.
- **Use Web3Forms instead:** some networks block formsubmit.co (for example some VPN or
  ad-blocking DNS filters), so emails from visitors on those networks do not go through, though
  WhatsApp still works. To avoid this, get a free access key at <https://web3forms.com> by
  entering the owner's email; the key arrives in that inbox. Paste it into `web3formsKey:` in
  `js/enquiry.js`. When a key is filled in, emails go through Web3Forms instead of FormSubmit.

If an email does not arrive, open the browser console (F12). A line starting with
`[LSI_Enquiry]` gives the reason. To change the WhatsApp number, edit `whatsapp` in the same
settings block.

## Store e-book (access code)

The Share Market Store page shows the e-book without a price or Buy button. A visitor enters
the access code and the e-book opens inside the Store page:

- **Code:** `LSIRA` (upper or lower case both work)
- **PDF location:** `pdf/lsi-ebook.pdf` (currently *Share Market Made Easy*, 164 pages). To
  change the book, replace that file with another PDF of the same name, or change `EBOOK_PDF`
  at the top of `js/ebook-access.js`. If the file is missing, the reader shows "The e-book file
  has not been added yet".
- To change the code, edit `ACCESS_CODE` in `js/ebook-access.js`.

On computers the browser's own PDF viewer is used. On phones and tablets the pages are drawn
with PDF.js (loaded from cdnjs only when needed).

Because the site has no server, the code and the PDF address can be read by anyone who looks
at the page source. The code keeps casual visitors out; it is not strong protection for a paid
product.

## Courses

The Share Market course page shows the course, fee and curriculum, with the message
"This course will be available soon in our Store." in place of the Buy buttons. The
"Ask a Question First" button opens the enquiry pop-up.

## Languages (English / हिंदी / मराठी)

The language switcher translates all visible text using the dictionaries in `js/i18n/`
(`common.js` plus one file per page). When you add or change text on a page:

1. Add the English text with its Hindi and Marathi translation to that page's file in `js/i18n/`.
2. Increase `I18N_VERSION` in `js/lang.js` so browsers download the new dictionary.

## After changing CSS or JavaScript

Pages load files with a version, for example `css/style.css?v=20260925b`. After editing a file,
change the `?v=` value in the pages that load it so visitors get the new copy.
