# PyDev? +beautify!
Below modification made to add more beautifull look on PyDev.
No special code added, but I've modify the resources (.PNG, .GIF)


<table style="border: none;">
  <tbody>
    <tr style="border:none;">
      <td style="vertical-align: top; padding-top: 10px; border: none;">

* Python File icon, 

to be used in dark theme 

or dark background
      </td>
      <td style="vertical-align: top; padding-top: 10px; border: none;">
		<img src="https://raw.github.com/x2nie/Pydev/development/extra/python_file_icon_bug.png" />
      </td>
      <td style="vertical-align: top; padding-top: 10px; border: none;">
		<img src="https://raw.github.com/x2nie/Pydev/development/extra/python_file_icon_bugfixed.png" />
      </td>
    </tr>

    <tr style="border:none;">
      <td style="vertical-align: top; padding-top: 10px; border: none;">

* Python Logotext, 

in New Project wizard
      </td>
      <td style="vertical-align: top; padding-top: 10px; border: none;">

      </td>
      <td style="vertical-align: top; padding-top: 10px; border: none;">
		<img src="https://raw.github.com/x2nie/Pydev/development/extra/pydev_newprojectwizard1.png" />      </td>
    </tr>

    <tr style="border:none;">
      <td style="vertical-align: top; padding-top: 10px; border: none;">

* Python Logo separated 

(different picture), 

in header of 

New Project wizard
      </td>
      <td style="vertical-align: top; padding-top: 10px; border: none;">

      </td>
      <td style="vertical-align: top; padding-top: 10px; border: none;">
		<img src="https://raw.github.com/x2nie/Pydev/development/extra/pydev_newprojectwizard2.png" />      </td>
    </tr>

    <tr style="border:none;">
      <td style="vertical-align: top; padding-top: 10px; border: none;">
* Python interpreter logo 
in PyDev Explorer 
and in perspective
      </td>
      <td style="vertical-align: top; padding-top: 10px; border: none;">

      </td>
      <td style="vertical-align: top; padding-top: 10px; border: none;">
		<img src="https://raw.github.com/x2nie/Pydev/development/extra/python_icon_in_explorer.png" />      </td>
    </tr>

    <tr style="border:none;">
      <td style="vertical-align: top; padding-top: 10px; border: none;">

      </td>
      <td style="vertical-align: top; padding-top: 10px; border: none;">

      </td>
      <td style="vertical-align: top; padding-top: 10px; border: none;">
	</td>
    </tr>
  </tbody>
</table>

## Minor bug of Pydev -&gt;&gt; Eclipse 4 CSS / Theme
Pydev has potential problem when deal with CSS of Eclipse 4.x (I use Kepler / Eclipse 4.3),
since the class "StyledText" has been changed into "StyledTextWithoutVerticalBar".

For example: 
* If you are using CSS/Style enabled plugin like the famous [Jeeeyul's Eclipse 4 Chrome Theme](https://github.com/jeeeyul/eclipse-themes), and set the line text color and line style into "dashed",
You would'nt see dashed line in Pydev Editor, while in all other Eclipse's editor (such Java Editor) those styles are applied.
![screenshot](https://raw.github.com/x2nie/Pydev/development/extra/pyeditor_css_bug3.gif)
![screenshot](https://raw.github.com/x2nie/Pydev/development/extra/pyeditor_css_bug2.png)

IMHO, it's not limited to the line text color, nor Jeeyul plugin only...
It would occured in all plugin that look for "StyleText" class name like for hooking the Theme/CSS.


##Solution : 
1. I hope the Pydev team change back the "StyledTextWithoutVerticalBar" class name into "StyledText". This will solve this issue.
2. We need to hack another eclipse's plugins to recognize the "StyledTextWithoutVerticalBar" class as "StyledText". This is stupid solution due the problem is inside pydev, not other plugin. Atlease, this solution solve the pydev bug.
3. If you are has no time to reinvent the wheel, then you might need to just use my stupid solution which is a fork of Jeeeyul's work, that allow PyDev to be styled as other editor. Found here: https://github.com/x2nie/eclipse-themes
4. Perhaps, someone need me to solve the problem by editing the Pydev? Yeah, I want to; but this solution (number 1) <b>is too easy</b> to be done by everyone knows Pydev. Isn't it?.

Oke, below is what should Pydev has. 

![screenshot](https://raw.github.com/x2nie/Pydev/development/extra/pyeditor_css_bug.png)

This is done by applying the solution number 2 above.

cheers !!!
:-)
x2nie

