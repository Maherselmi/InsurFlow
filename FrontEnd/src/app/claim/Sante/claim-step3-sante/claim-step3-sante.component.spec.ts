import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ClaimStep3SanteComponent } from './claim-step3-sante.component';

describe('ClaimStep3SanteComponent', () => {
  let component: ClaimStep3SanteComponent;
  let fixture: ComponentFixture<ClaimStep3SanteComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ClaimStep3SanteComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(ClaimStep3SanteComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
