import { Pipe, PipeTransform, SecurityContext } from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';

@Pipe({
  name: 'formatMarkdown',
  standalone: true
})
export class FormatMarkdownPipe implements PipeTransform {
  constructor(private sanitizer: DomSanitizer) {}

  transform(value: string): string {
    if (!value) return '';

    // Extract code blocks first, replacing with placeholders
    const codeBlocks: string[] = [];
    let html = value.replace(/```(\w+)?\n([\s\S]*?)```/g, (_, lang, code) => {
      const index = codeBlocks.length;
      codeBlocks.push(
        `<pre><code class="language-${this.escapeHtml(lang || 'text')}">${this.escapeHtml(code.trim())}</code></pre>`
      );
      return `%%CODEBLOCK_${index}%%`;
    });

    // Extract inline code
    const inlineCodes: string[] = [];
    html = html.replace(/`([^`]+)`/g, (_, code) => {
      const index = inlineCodes.length;
      inlineCodes.push(`<code>${this.escapeHtml(code)}</code>`);
      return `%%INLINE_${index}%%`;
    });

    // Now escape the remaining HTML
    html = this.escapeHtml(html);

    // Convert headers
    html = html.replace(/^### (.+)$/gm, '<h3>$1</h3>');
    html = html.replace(/^## (.+)$/gm, '<h2>$1</h2>');
    html = html.replace(/^# (.+)$/gm, '<h1>$1</h1>');

    // Convert bold
    html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');

    // Convert italic
    html = html.replace(/\*(.+?)\*/g, '<em>$1</em>');

    // Convert unordered lists
    html = html.replace(/^- (.+)$/gm, '<li>$1</li>');
    html = html.replace(/(<li>.*<\/li>\n?)+/g, '<ul>$&</ul>');

    // Convert numbered lists
    html = html.replace(/^\d+\. (.+)$/gm, '<li>$1</li>');

    // Convert line breaks
    html = html.replace(/\n\n/g, '</p><p>');
    html = html.replace(/\n/g, '<br>');

    // Wrap in paragraph if not already wrapped
    if (!html.startsWith('<')) {
      html = `<p>${html}</p>`;
    }

    // Clean up empty paragraphs
    html = html.replace(/<p><\/p>/g, '');
    html = html.replace(/<p><br><\/p>/g, '');

    // Restore code blocks and inline code
    codeBlocks.forEach((block, i) => {
      html = html.replace(`%%CODEBLOCK_${i}%%`, block);
    });
    inlineCodes.forEach((code, i) => {
      html = html.replace(`%%INLINE_${i}%%`, code);
    });

    // Sanitize the final HTML
    return this.sanitizer.sanitize(SecurityContext.HTML, html) || '';
  }

  private escapeHtml(text: string): string {
    return text
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }
}
